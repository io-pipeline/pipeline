package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.repository.v1.*;
import io.pipeline.repository.filesystem.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Filesystem-based implementation of PipeDocRepository using the FilesystemService.
 * Provides full CRUD operations for PipeDocs and ModuleProcessRequests.
 * Organizes documents in a hierarchical folder structure.
 */
@GrpcService
public class PipeDocRepositoryServiceImpl extends MutinyPipeDocRepositoryGrpc.PipeDocRepositoryImplBase {
    
    private static final Logger LOG = Logger.getLogger(PipeDocRepositoryServiceImpl.class);
    
    // Standard folder names
    private static final String PIPEDOCS_FOLDER = "PipeDocs";
    private static final String REQUESTS_FOLDER = "Process Requests";
    private static final String SEED_DATA_FOLDER = "Seed Data";
    
    @Inject
    RepositoryFilesystemHelper filesystemHelper;
    
    @Inject
    @GrpcService
    FilesystemServiceImpl filesystemService;
    
    @Override
    public Uni<CreatePipeDocResponse> createPipeDoc(CreatePipeDocRequest request) {
        return ensureFolderStructure()
            .flatMap(ignored -> {
                // Get or create the appropriate folder
                return getOrCreatePipeDocFolderUni()
                    .flatMap(parentFolderId -> {
                        try {
                            // Save using filesystem helper - wrap in Uni to run on worker thread
                            PipeDoc doc = request.getDocument();
                            return Uni.createFrom().item(() -> {
                                // First save the document
                                Node node = filesystemHelper.savePipeDoc(parentFolderId, doc, "Repository");
                                
                                // Then update with additional metadata (tags and description)
                                Map<String, String> additionalMetadata = new HashMap<>(node.getMetadataMap());
                                additionalMetadata.putAll(request.getTagsMap());
                                additionalMetadata.put("description", request.getDescription());
                                
                                UpdateNodeRequest updateRequest = UpdateNodeRequest.newBuilder()
                                    .setId(node.getId())
                                    .setName(node.getName())
                                    // Don't include payload - it's already stored
                                    .putAllMetadata(additionalMetadata)
                                    .build();
                                
                                return filesystemService.updateNode(updateRequest)
                                    .await().indefinitely();
                            }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                            .map(node -> {
                                // Build response
                                StoredPipeDoc stored = StoredPipeDoc.newBuilder()
                                    .setStorageId(node.getId())
                                    .setDocument(doc)
                                    .putAllTags(request.getTagsMap())
                                    .setDescription(request.getDescription())
                                    .setCreatedAt(instantToTimestamp(Instant.now()))
                                    .setUpdatedAt(instantToTimestamp(Instant.now()))
                                    .build();
                                
                                return CreatePipeDocResponse.newBuilder()
                                    .setStorageId(node.getId())
                                    .setStoredDocument(stored)
                                    .build();
                            });
                        } catch (Exception e) {
                            LOG.error("Error creating PipeDoc", e);
                            return Uni.createFrom().failure(
                                new StatusRuntimeException(
                                    Status.INTERNAL.withDescription("Failed to create PipeDoc: " + e.getMessage())
                                )
                            );
                        }
                    });
            });    
    }
    
    @Override
    public Uni<StoredPipeDoc> getPipeDoc(GetPipeDocRequest request) {
        return filesystemService.getNode(
                GetNodeRequest.newBuilder()
                    .setId(request.getStorageId())
                    .build()
            )
            .flatMap(node -> {
                try {
                    LOG.debugf("Retrieved node: id=%s, name=%s, hasPayload=%s, payloadTypeUrl=%s", 
                        node.getId(), node.getName(), node.hasPayload(), 
                        node.hasPayload() ? node.getPayload().getTypeUrl() : "N/A");
                    
                    // Extract PipeDoc from node
                    PipeDoc doc = filesystemHelper.extractPipeDoc(node);
                    if (doc == null) {
                        return Uni.createFrom().failure(
                            new StatusRuntimeException(
                                Status.NOT_FOUND.withDescription("Node does not contain a PipeDoc")
                            )
                        );
                    }
                    
                    LOG.debugf("Extracted PipeDoc: id=%s, title=%s", doc.getId(), doc.getTitle());
                    
                    // Build StoredPipeDoc from node metadata
                    Map<String, String> tags = new HashMap<>(node.getMetadataMap());
                    String description = tags.remove("description");
                    if (description == null) {
                        description = "";
                    }
                    
                    StoredPipeDoc stored = StoredPipeDoc.newBuilder()
                        .setStorageId(node.getId())
                        .setDocument(doc)
                        .putAllTags(tags)
                        .setDescription(description)
                        .setCreatedAt(node.getCreatedAt())
                        .setUpdatedAt(node.getUpdatedAt())
                        .build();
                    
                    return Uni.createFrom().item(stored);
                    
                } catch (Exception e) {
                    LOG.error("Error extracting PipeDoc from node", e);
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.INTERNAL.withDescription("Failed to extract PipeDoc: " + e.getMessage())
                        )
                    );
                }
            })
            .onFailure().transform(e -> {
                if (e instanceof StatusRuntimeException) {
                    return e;
                }
                LOG.error("Error retrieving PipeDoc", e);
                return new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription("PipeDoc not found with id: " + request.getStorageId())
                );
            });
    }
    
    @Override
    public Uni<StoredPipeDoc> updatePipeDoc(UpdatePipeDocRequest request) {
        // Get existing node first
        return filesystemService.getNode(
                GetNodeRequest.newBuilder()
                    .setId(request.getStorageId())
                    .build()
            )
            .flatMap(existingNode -> {
                // Prepare updated metadata
                Map<String, String> metadata = new HashMap<>(request.getTagsMap());
                metadata.put("description", request.getDescription());
                metadata.put("documentId", request.getDocument().getId());
                if (request.getDocument().hasSourceUri()) {
                    metadata.put("sourceUri", request.getDocument().getSourceUri());
                }
                
                // Update the node
                UpdateNodeRequest updateRequest = UpdateNodeRequest.newBuilder()
                    .setId(request.getStorageId())
                    .setName(request.getDocument().getTitle() != null ? 
                        request.getDocument().getTitle() : existingNode.getName())
                    .setPayload(Any.pack(request.getDocument()))
                    .putAllMetadata(metadata)
                    .build();
                
                return filesystemService.updateNode(updateRequest)
                    .map(updatedNode -> {
                        // Build StoredPipeDoc from updated node
                        Map<String, String> tags = new HashMap<>(updatedNode.getMetadataMap());
                        String description = tags.remove("description");
                        if (description == null) {
                            description = "";
                        }
                        
                        return StoredPipeDoc.newBuilder()
                            .setStorageId(updatedNode.getId())
                            .setDocument(request.getDocument())
                            .putAllTags(tags)
                            .setDescription(description)
                            .setCreatedAt(existingNode.getCreatedAt())
                            .setUpdatedAt(instantToTimestamp(Instant.now()))
                            .build();
                    });
            })
            .onFailure().<StoredPipeDoc>transform(e -> {
                if (e instanceof StatusRuntimeException) {
                    return e;
                }
                LOG.error("Error updating PipeDoc", e);
                return new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription("PipeDoc not found with id: " + request.getStorageId())
                );
            });
    }
    
    @Override
    public Uni<Empty> deletePipeDoc(DeletePipeDocRequest request) {
        return filesystemService.deleteNode(
                DeleteNodeRequest.newBuilder()
                    .setId(request.getStorageId())
                    .build()
            )
            .map(response -> {
                if (!response.getSuccess()) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("PipeDoc not found with id: " + request.getStorageId())
                    );
                }
                return Empty.getDefaultInstance();
            })
            .onFailure().transform(e -> {
                if (e instanceof StatusRuntimeException) {
                    return e;
                }
                LOG.error("Error deleting PipeDoc", e);
                return new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription("PipeDoc not found with id: " + request.getStorageId())
                );
            });
    }
    
    @Override
    public Uni<ListPipeDocsResponse> listPipeDocs(ListPipeDocsRequest request) {
        // Get the PipeDocs folder
        return getOrCreatePipeDocFolderUni()
            .flatMap(folderId -> {
                // Search for all PipeDoc nodes in the folder
                SearchNodesRequest searchRequest = SearchNodesRequest.newBuilder()
                    .setQuery("") // Empty query to get all
                    .addPaths("/" + PIPEDOCS_FOLDER)
                    .addTypes(Node.NodeType.FILE)
                    .setPageSize(request.getPageSize() > 0 ? request.getPageSize() : 100)
                    .build();
                
                return filesystemService.searchNodes(searchRequest)
                    .flatMap(searchResponse -> {
                        // Convert nodes to StoredPipeDocs
                        List<Uni<StoredPipeDoc>> docUnis = new ArrayList<>();
                        
                        for (Node node : searchResponse.getNodesList()) {
                            if (node.hasPayload()) {
                                Uni<StoredPipeDoc> docUni = Uni.createFrom().item(() -> {
                                    try {
                                        PipeDoc doc = filesystemHelper.extractPipeDoc(node);
                                        if (doc != null) {
                                            Map<String, String> tags = new HashMap<>(node.getMetadataMap());
                                            String description = tags.remove("description");
                                            if (description == null) {
                                                description = "";
                                            }
                                            
                                            return StoredPipeDoc.newBuilder()
                                                .setStorageId(node.getId())
                                                .setDocument(doc)
                                                .putAllTags(tags)
                                                .setDescription(description)
                                                .setCreatedAt(node.getCreatedAt())
                                                .setUpdatedAt(node.getUpdatedAt())
                                                .build();
                                        }
                                    } catch (Exception e) {
                                        LOG.error("Failed to extract PipeDoc from node", e);
                                    }
                                    return null;
                                });
                                docUnis.add(docUni);
                            }
                        }
                        
                        return Uni.combine().all().unis(docUnis)
                            .with(results -> {
                                List<StoredPipeDoc> docs = results.stream()
                                    .filter(Objects::nonNull)
                                    .map(o -> (StoredPipeDoc) o)
                                    .collect(Collectors.toList());
                                
                                // Build response
                                return ListPipeDocsResponse.newBuilder()
                                    .addAllDocuments(docs)
                                    .setTotalCount(searchResponse.getTotalCount())
                                    .build();
                            });
                    });
            });
    }


    @Override
    public Uni<SaveProcessRequestResponse> saveProcessRequest(SaveProcessRequestRequest request) {
        return ensureFolderStructure()
            .flatMap(ignored -> {
                // Get or create the requests folder
                return getOrCreateRequestsFolderUni()
                    .flatMap(parentFolderId -> {
                        try {
                            // Create metadata
                            Map<String, String> metadata = new HashMap<>(request.getTagsMap());
                            metadata.put("name", request.getName());
                            metadata.put("description", request.getDescription());
                            
                            // Save using filesystem helper - wrap in Uni to run on worker thread
                            ModuleProcessRequest processRequest = request.getRequest();
                            String moduleName = processRequest.hasMetadata() ? 
                                processRequest.getMetadata().getPipeStepName() : "Unknown";
                            
                            return Uni.createFrom().item(() -> 
                                filesystemHelper.saveModuleRequest(parentFolderId, processRequest, moduleName)
                            ).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                            .map(node -> {
                                // Build response
                                StoredProcessRequest stored = StoredProcessRequest.newBuilder()
                                    .setStorageId(node.getId())
                                    .setRequest(processRequest)
                                    .setName(request.getName())
                                    .setDescription(request.getDescription())
                                    .putAllTags(request.getTagsMap())
                                    .setCreatedAt(instantToTimestamp(Instant.now()))
                                    .setUpdatedAt(instantToTimestamp(Instant.now()))
                                    .build();
                                
                                return SaveProcessRequestResponse.newBuilder()
                                    .setStorageId(node.getId())
                                    .setStoredRequest(stored)
                                    .build();
                            });
                        } catch (Exception e) {
                            LOG.error("Error saving ProcessRequest", e);
                            return Uni.createFrom().failure(
                                new StatusRuntimeException(
                                    Status.INTERNAL.withDescription("Failed to save ProcessRequest: " + e.getMessage())
                                )
                            );
                        }
                    });
            });
    }
    
    @Override
    public Uni<StoredProcessRequest> getProcessRequest(GetProcessRequestRequest request) {
        return filesystemService.getNode(
                GetNodeRequest.newBuilder()
                    .setId(request.getStorageId())
                    .build()
            )
            .flatMap(node -> {
                try {
                    // Extract ModuleProcessRequest from node
                    if (!node.hasPayload() || !node.getPayload().is(ModuleProcessRequest.class)) {
                        return Uni.createFrom().failure(
                            new StatusRuntimeException(
                                Status.NOT_FOUND.withDescription("Node does not contain a ProcessRequest")
                            )
                        );
                    }
                    
                    ModuleProcessRequest processRequest = node.getPayload().unpack(ModuleProcessRequest.class);
                    
                    // Build StoredProcessRequest from node metadata
                    Map<String, String> metadata = node.getMetadataMap();
                    String name = metadata.getOrDefault("name", "");
                    String description = metadata.getOrDefault("description", "");
                    
                    // Extract tags (exclude internal metadata)
                    Map<String, String> tags = new HashMap<>();
                    for (Map.Entry<String, String> entry : metadata.entrySet()) {
                        if (!entry.getKey().equals("name") && 
                            !entry.getKey().equals("description") &&
                            !entry.getKey().equals("module") &&
                            !entry.getKey().equals("pipelineName") &&
                            !entry.getKey().equals("stepName") &&
                            !entry.getKey().equals("streamId")) {
                            tags.put(entry.getKey(), entry.getValue());
                        }
                    }
                    
                    StoredProcessRequest stored = StoredProcessRequest.newBuilder()
                        .setStorageId(node.getId())
                        .setRequest(processRequest)
                        .setName(name)
                        .setDescription(description)
                        .putAllTags(tags)
                        .setCreatedAt(node.getCreatedAt())
                        .setUpdatedAt(node.getUpdatedAt())
                        .build();
                    
                    return Uni.createFrom().item(stored);
                    
                } catch (Exception e) {
                    LOG.error("Error extracting ProcessRequest from node", e);
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.INTERNAL.withDescription("Failed to extract ProcessRequest: " + e.getMessage())
                        )
                    );
                }
            })
            .onFailure().transform(e -> {
                if (e instanceof StatusRuntimeException) {
                    return e;
                }
                LOG.error("Error retrieving ProcessRequest", e);
                return new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription("ProcessRequest not found with id: " + request.getStorageId())
                );
            });
    }
    
    @Override
    public Uni<ListProcessRequestsResponse> listProcessRequests(ListProcessRequestsRequest request) {
        // Get the requests folder
        return getOrCreateRequestsFolderUni()
            .flatMap(folderId -> {
                // Search for all ProcessRequest nodes in the folder
                SearchNodesRequest searchRequest = SearchNodesRequest.newBuilder()
                    .setQuery("") // Empty query to get all
                    .addPaths("/" + REQUESTS_FOLDER)
                    .addTypes(Node.NodeType.FILE)
                    .setPageSize(request.getPageSize() > 0 ? request.getPageSize() : 100)
                    .build();
                
                return filesystemService.searchNodes(searchRequest)
                    .flatMap(searchResponse -> {
                        // Convert nodes to StoredProcessRequests
                        List<Uni<StoredProcessRequest>> requestUnis = new ArrayList<>();
                        
                        for (Node node : searchResponse.getNodesList()) {
                            if (node.hasPayload() && node.getPayload().is(ModuleProcessRequest.class)) {
                                Uni<StoredProcessRequest> requestUni = Uni.createFrom().item(() -> {
                                    try {
                                        ModuleProcessRequest processRequest = node.getPayload().unpack(ModuleProcessRequest.class);
                                        
                                        Map<String, String> metadata = node.getMetadataMap();
                                        String name = metadata.getOrDefault("name", "");
                                        String description = metadata.getOrDefault("description", "");
                                        
                                        // Extract tags
                                        Map<String, String> tags = new HashMap<>();
                                        for (Map.Entry<String, String> entry : metadata.entrySet()) {
                                            if (!entry.getKey().equals("name") && 
                                                !entry.getKey().equals("description") &&
                                                !entry.getKey().equals("module") &&
                                                !entry.getKey().equals("pipelineName") &&
                                                !entry.getKey().equals("stepName") &&
                                                !entry.getKey().equals("streamId")) {
                                                tags.put(entry.getKey(), entry.getValue());
                                            }
                                        }
                                        
                                        return StoredProcessRequest.newBuilder()
                                            .setStorageId(node.getId())
                                            .setRequest(processRequest)
                                            .setName(name)
                                            .setDescription(description)
                                            .putAllTags(tags)
                                            .setCreatedAt(node.getCreatedAt())
                                            .setUpdatedAt(node.getUpdatedAt())
                                            .build();
                                    } catch (Exception e) {
                                        LOG.error("Failed to extract ProcessRequest from node", e);
                                        return null;
                                    }
                                });
                                requestUnis.add(requestUni);
                            }
                        }
                        
                        return Uni.combine().all().unis(requestUnis)
                            .with(results -> {
                                List<StoredProcessRequest> requests = results.stream()
                                    .filter(Objects::nonNull)
                                    .map(o -> (StoredProcessRequest) o)
                                    .collect(Collectors.toList());
                                
                                return ListProcessRequestsResponse.newBuilder()
                                    .addAllRequests(requests)
                                    .setTotalCount(searchResponse.getTotalCount())
                                    .build();
                            });
                    });
            });
    }
    
    @Override
    public Uni<Empty> deleteProcessRequest(DeleteProcessRequestRequest request) {
        return filesystemService.deleteNode(
                DeleteNodeRequest.newBuilder()
                    .setId(request.getStorageId())
                    .build()
            )
            .map(response -> {
                if (!response.getSuccess()) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("ProcessRequest not found with id: " + request.getStorageId())
                    );
                }
                return Empty.getDefaultInstance();
            })
            .onFailure().transform(e -> {
                if (e instanceof StatusRuntimeException) {
                    return e;
                }
                LOG.error("Error deleting ProcessRequest", e);
                return new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription("ProcessRequest not found with id: " + request.getStorageId())
                );
            });
    }
    
    // Helper methods
    
    private Uni<Void> ensureFolderStructure() {
        // Create standard folders if they don't exist - run on worker thread
        return Uni.createFrom().item(() -> {
                try {
                    filesystemHelper.createStandardFolders();
                } catch (Exception e) {
                    LOG.debug("Standard folders may already exist", e);
                }
                return null;
            })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            .replaceWithVoid();
    }
    
    private Uni<String> getOrCreatePipeDocFolderUni() {
        // Search for existing folder
        SearchNodesRequest searchRequest = SearchNodesRequest.newBuilder()
            .setQuery(PIPEDOCS_FOLDER)
            .addTypes(Node.NodeType.FOLDER)
            .setPageSize(1)
            .build();
        
        return filesystemService.searchNodes(searchRequest)
            .flatMap(response -> {
                if (response.getNodesCount() > 0) {
                    return Uni.createFrom().item(response.getNodes(0).getId());
                }
                
                // Create if not exists - run on worker thread to avoid blocking event loop
                return Uni.createFrom().item(() -> {
                    Node folder = filesystemHelper.createFolder(null, PIPEDOCS_FOLDER, 
                        Map.of("type", "pipedocs", "description", "Storage for PipeDoc documents"));
                    return folder.getId();
                }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
            })
            .onFailure().transform(e -> {
                LOG.error("Error getting/creating PipeDocs folder", e);
                return new RuntimeException("Failed to get/create PipeDocs folder", e);
            });
    }
    
    private Uni<String> getOrCreateRequestsFolderUni() {
        // Search for existing folder
        SearchNodesRequest searchRequest = SearchNodesRequest.newBuilder()
            .setQuery(REQUESTS_FOLDER)
            .addTypes(Node.NodeType.FOLDER)
            .setPageSize(1)
            .build();
        
        return filesystemService.searchNodes(searchRequest)
            .flatMap(response -> {
                if (response.getNodesCount() > 0) {
                    return Uni.createFrom().item(response.getNodes(0).getId());
                }
                
                // Create if not exists - run on worker thread to avoid blocking event loop
                return Uni.createFrom().item(() -> {
                    Node folder = filesystemHelper.createFolder(null, REQUESTS_FOLDER, 
                        Map.of("type", "requests", "description", "Storage for process requests"));
                    return folder.getId();
                }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
            })
            .onFailure().transform(e -> {
                LOG.error("Error getting/creating Process Requests folder", e);
                return new RuntimeException("Failed to get/create Process Requests folder", e);
            });
    }
    
    private Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }
    
    // Streaming methods - TODO: Implement these
    
    @Override
    public Multi<BatchOperationResult> batchCreatePipeDocs(Multi<CreatePipeDocRequest> request) {
        // TODO: Implement batch create
        return Multi.createFrom().failure(new UnsupportedOperationException("Not implemented yet"));
    }
    
    @Override
    public Multi<ExportChunk> exportPipeDocs(ExportPipeDocsRequest request) {
        // TODO: Implement export
        return Multi.createFrom().failure(new UnsupportedOperationException("Not implemented yet"));
    }
    
    @Override
    public Uni<ImportPipeDocsResponse> importPipeDocs(Multi<ImportChunk> request) {
        // TODO: Implement import
        return Uni.createFrom().failure(new UnsupportedOperationException("Not implemented yet"));
    }
    
    @Override
    public Uni<FormatRepositoryResponse> formatRepository(FormatRepositoryRequest request) {
        // Validate confirmation
        if (!"DELETE_REPOSITORY_DATA".equals(request.getConfirmation())) {
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INVALID_ARGUMENT.withDescription("Confirmation must be 'DELETE_REPOSITORY_DATA'")
                )
            );
        }
        
        boolean includeDocuments = request.getIncludeDocuments();
        boolean includeRequests = request.getIncludeRequests();
        boolean dryRun = request.getDryRun();
        
        // If neither is specified, clear both
        if (!includeDocuments && !includeRequests) {
            includeDocuments = true;
            includeRequests = true;
        }
        
        // Use the filesystem FormatFilesystem to clear repository folders
        List<String> typeUrls = new ArrayList<>();
        if (includeDocuments) {
            typeUrls.add(Any.pack(PipeDoc.getDefaultInstance()).getTypeUrl());
        }
        if (includeRequests) {
            typeUrls.add(Any.pack(ModuleProcessRequest.getDefaultInstance()).getTypeUrl());
            typeUrls.add(Any.pack(ModuleProcessResponse.getDefaultInstance()).getTypeUrl());
        }
        
        // Create FormatFilesystem request to clear specific types
        FormatFilesystemRequest formatRequest = FormatFilesystemRequest.newBuilder()
            .setConfirmation("DELETE_FILESYSTEM_DATA")
            .setDryRun(dryRun)
            .addAllTypeUrls(typeUrls)
            .build();
        
        return filesystemService.formatFilesystem(formatRequest)
            .map(formatResponse -> {
                // Extract counts by type
                int docsDeleted = 0;
                int requestsDeleted = 0;
                
                String pipeDocTypeUrl = Any.pack(PipeDoc.getDefaultInstance()).getTypeUrl();
                String requestTypeUrl = Any.pack(ModuleProcessRequest.getDefaultInstance()).getTypeUrl();
                String responseTypeUrl = Any.pack(ModuleProcessResponse.getDefaultInstance()).getTypeUrl();
                
                for (Map.Entry<String, Integer> entry : formatResponse.getDeletedByTypeMap().entrySet()) {
                    if (entry.getKey().equals(pipeDocTypeUrl)) {
                        docsDeleted = entry.getValue();
                    } else if (entry.getKey().equals(requestTypeUrl) || entry.getKey().equals(responseTypeUrl)) {
                        requestsDeleted += entry.getValue();
                    }
                }
                
                String message = dryRun ?
                    String.format("Would delete %d documents and %d requests", docsDeleted, requestsDeleted) :
                    String.format("Deleted %d documents and %d requests", docsDeleted, requestsDeleted);
                
                FormatRepositoryResponse.Builder responseBuilder = FormatRepositoryResponse.newBuilder()
                    .setSuccess(formatResponse.getSuccess())
                    .setMessage(message)
                    .setDocumentsDeleted(docsDeleted)
                    .setRequestsDeleted(requestsDeleted);
                
                // Add deleted IDs for dry run
                if (dryRun) {
                    responseBuilder.addAllDeletedIds(formatResponse.getDeletedPathsList());
                }
                
                return responseBuilder.build();
            })
            .onFailure().transform(e -> {
                LOG.error("Error formatting repository", e);
                return new StatusRuntimeException(
                    Status.INTERNAL.withDescription("Failed to format repository: " + e.getMessage())
                );
            });
    }
}