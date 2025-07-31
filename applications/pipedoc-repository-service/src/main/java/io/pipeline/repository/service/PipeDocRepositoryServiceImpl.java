package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.repository.v1.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based implementation of PipeDocRepository using the generic repository.
 * Provides full CRUD operations for PipeDocs and ModuleProcessRequests.
 */
@GrpcService
public class PipeDocRepositoryServiceImpl extends MutinyPipeDocRepositoryGrpc.PipeDocRepositoryImplBase {
    
    private static final Logger LOG = Logger.getLogger(PipeDocRepositoryServiceImpl.class);
    
    @Inject
    GenericRepositoryService repository;
    
    @Override
    public Uni<CreatePipeDocResponse> createPipeDoc(CreatePipeDocRequest request) {
        try {
            // Extract metadata
            Map<String, String> metadata = new HashMap<>(request.getTagsMap());
            metadata.put("_description", request.getDescription());
            
            // Extract searchable fields from PipeDoc
            PipeDoc doc = request.getDocument();
            metadata.put("documentId", doc.getId());
            if (doc.hasSourceUri()) {
                metadata.put("sourceUri", doc.getSourceUri());
            }
            if (doc.hasTitle()) {
                metadata.put("title", doc.getTitle());
            }
            
            // Store the document
            return repository.store(doc, metadata)
                .map(storageId -> {
                    // Build response
                    StoredPipeDoc stored = StoredPipeDoc.newBuilder()
                        .setStorageId(storageId)
                        .setDocument(doc)
                        .putAllTags(request.getTagsMap())
                        .setDescription(request.getDescription())
                        .setCreatedAt(instantToTimestamp(Instant.now()))
                        .setUpdatedAt(instantToTimestamp(Instant.now()))
                        .build();
                    
                    return CreatePipeDocResponse.newBuilder()
                        .setStorageId(storageId)
                        .setStoredDocument(stored)
                        .build();
                })
                .onFailure().transform(e -> {
                    LOG.error("Error creating PipeDoc", e);
                    return new StatusRuntimeException(
                        Status.INTERNAL.withDescription("Failed to create PipeDoc: " + e.getMessage())
                    );
                });
            
        } catch (Exception e) {
            LOG.error("Error creating PipeDoc", e);
            return Uni.createFrom().failure(
                new StatusRuntimeException(
                    Status.INTERNAL.withDescription("Failed to create PipeDoc: " + e.getMessage())
                )
            );
        }
    }
    
    @Override
    public Uni<StoredPipeDoc> getPipeDoc(GetPipeDocRequest request) {
        return repository.get(request.getStorageId(), PipeDoc.class)
            .flatMap(doc -> {
                if (doc == null) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("PipeDoc not found with id: " + request.getStorageId())
                        )
                    );
                }
                
                // Get metadata
                return repository.getMetadata(request.getStorageId())
                    .map(metadata -> buildStoredPipeDoc(request.getStorageId(), doc, metadata));
            })
            .onFailure().transform(e -> {
                if (e instanceof StatusRuntimeException) {
                    return e;
                }
                LOG.error("Error retrieving PipeDoc", e);
                return new StatusRuntimeException(
                    Status.INTERNAL.withDescription("Failed to retrieve PipeDoc: " + e.getMessage())
                );
            });
    }
    
    @Override
    public Uni<StoredPipeDoc> updatePipeDoc(UpdatePipeDocRequest request) {
        // First check if exists
        return repository.exists(request.getStorageId())
            .flatMap(exists -> {
                if (!exists) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("PipeDoc not found with id: " + request.getStorageId())
                        )
                    );
                }
                
                // Prepare updated metadata
                Map<String, String> metadata = new HashMap<>(request.getTagsMap());
                metadata.put("_description", request.getDescription());
                
                // Extract searchable fields
                PipeDoc doc = request.getDocument();
                metadata.put("documentId", doc.getId());
                if (doc.hasSourceUri()) {
                    metadata.put("sourceUri", doc.getSourceUri());
                }
                if (doc.hasTitle()) {
                    metadata.put("title", doc.getTitle());
                }
                
                // Update
                return repository.update(request.getStorageId(), doc, metadata)
                    .map(success -> {
                        if (!success) {
                            throw new StatusRuntimeException(
                                Status.INTERNAL.withDescription("Failed to update document")
                            );
                        }
                        return buildStoredPipeDoc(request.getStorageId(), doc, metadata);
                    });
            });
    }
    
    @Override
    public Uni<Empty> deletePipeDoc(DeletePipeDocRequest request) {
        return repository.delete(request.getStorageId())
            .map(deleted -> {
                if (!deleted) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("PipeDoc not found with id: " + request.getStorageId())
                    );
                }
                return Empty.getDefaultInstance();
            });
    }
    
    @Override
    public Uni<ListPipeDocsResponse> listPipeDocs(ListPipeDocsRequest request) {
        // For now, list all PipeDocs - filtering can be added later
        return repository.listByType(PipeDoc.class)
            .flatMap(ids -> {
                // Paginate if needed
                int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 100;
                int startIndex = 0;
                
                // Simple pagination using page token as index
                if (request.getPageToken() != null && !request.getPageToken().isEmpty()) {
                    try {
                        startIndex = Integer.parseInt(request.getPageToken());
                    } catch (NumberFormatException e) {
                        // Invalid page token, start from beginning
                    }
                }
                
                int endIndex = Math.min(startIndex + pageSize, ids.size());
                List<String> pageIds = ids.subList(startIndex, endIndex);
                
                // Batch get the documents
                return repository.batchGetAny(pageIds)
                    .flatMap(anyMap -> {
                        List<Uni<StoredPipeDoc>> docUnis = new ArrayList<>();
                        
                        for (Map.Entry<String, Any> entry : anyMap.entrySet()) {
                            String id = entry.getKey();
                            Any any = entry.getValue();
                            
                            if (any.is(PipeDoc.class)) {
                                Uni<StoredPipeDoc> docUni = repository.getMetadata(id)
                                    .map(metadata -> {
                                        try {
                                            PipeDoc doc = any.unpack(PipeDoc.class);
                                            return buildStoredPipeDoc(id, doc, metadata);
                                        } catch (Exception e) {
                                            LOG.error("Failed to unpack PipeDoc", e);
                                            return null;
                                        }
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
                                ListPipeDocsResponse.Builder responseBuilder = ListPipeDocsResponse.newBuilder()
                                    .addAllDocuments(docs)
                                    .setTotalCount(ids.size());
                                
                                // Set next page token if there are more results
                                if (endIndex < ids.size()) {
                                    responseBuilder.setNextPageToken(String.valueOf(endIndex));
                                }
                                
                                return responseBuilder.build();
                            });
                    });
            });
    }
    
    @Override
    public Uni<SaveProcessRequestResponse> saveProcessRequest(SaveProcessRequestRequest request) {
        try {
            // Extract metadata
            Map<String, String> metadata = new HashMap<>(request.getTagsMap());
            metadata.put("_name", request.getName());
            metadata.put("_description", request.getDescription());
            metadata.put("_type", "ProcessRequest");
            
            // Extract additional searchable fields
            ModuleProcessRequest processRequest = request.getRequest();
            if (processRequest.hasDocument()) {
                metadata.put("documentId", processRequest.getDocument().getId());
            }
            if (processRequest.hasMetadata()) {
                metadata.put("pipelineName", processRequest.getMetadata().getPipelineName());
                metadata.put("stepName", processRequest.getMetadata().getPipeStepName());
            }
            
            // Store the request
            return repository.store(processRequest, metadata)
                .map(storageId -> {
                    // Build response
                    StoredProcessRequest stored = StoredProcessRequest.newBuilder()
                        .setStorageId(storageId)
                        .setRequest(processRequest)
                        .setName(request.getName())
                        .setDescription(request.getDescription())
                        .putAllTags(request.getTagsMap())
                        .setCreatedAt(instantToTimestamp(Instant.now()))
                        .setUpdatedAt(instantToTimestamp(Instant.now()))
                        .build();
                    
                    return SaveProcessRequestResponse.newBuilder()
                        .setStorageId(storageId)
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
    }
    
    @Override
    public Uni<StoredProcessRequest> getProcessRequest(GetProcessRequestRequest request) {
        return repository.get(request.getStorageId(), ModuleProcessRequest.class)
            .flatMap(processRequest -> {
                if (processRequest == null) {
                    return Uni.createFrom().failure(
                        new StatusRuntimeException(
                            Status.NOT_FOUND.withDescription("ProcessRequest not found with id: " + request.getStorageId())
                        )
                    );
                }
                
                // Get metadata
                return repository.getMetadata(request.getStorageId())
                    .map(metadata -> buildStoredProcessRequest(request.getStorageId(), processRequest, metadata));
            });
    }
    
    @Override
    public Uni<ListProcessRequestsResponse> listProcessRequests(ListProcessRequestsRequest request) {
        // List all ModuleProcessRequests
        return repository.listByType(ModuleProcessRequest.class)
            .flatMap(ids -> {
                // Similar pagination as listPipeDocs
                int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 100;
                int startIndex = 0;
                
                if (request.getPageToken() != null && !request.getPageToken().isEmpty()) {
                    try {
                        startIndex = Integer.parseInt(request.getPageToken());
                    } catch (NumberFormatException e) {
                        // Invalid page token
                    }
                }
                
                int endIndex = Math.min(startIndex + pageSize, ids.size());
                List<String> pageIds = ids.subList(startIndex, endIndex);
                
                // Batch get
                return repository.batchGetAny(pageIds)
                    .flatMap(anyMap -> {
                        List<Uni<StoredProcessRequest>> requestUnis = new ArrayList<>();
                        
                        for (Map.Entry<String, Any> entry : anyMap.entrySet()) {
                            String id = entry.getKey();
                            Any any = entry.getValue();
                            
                            if (any.is(ModuleProcessRequest.class)) {
                                Uni<StoredProcessRequest> requestUni = repository.getMetadata(id)
                                    .map(metadata -> {
                                        try {
                                            ModuleProcessRequest req = any.unpack(ModuleProcessRequest.class);
                                            return buildStoredProcessRequest(id, req, metadata);
                                        } catch (Exception e) {
                                            LOG.error("Failed to unpack ModuleProcessRequest", e);
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
                                
                                ListProcessRequestsResponse.Builder responseBuilder = ListProcessRequestsResponse.newBuilder()
                                    .addAllRequests(requests)
                                    .setTotalCount(ids.size());
                                
                                if (endIndex < ids.size()) {
                                    responseBuilder.setNextPageToken(String.valueOf(endIndex));
                                }
                                
                                return responseBuilder.build();
                            });
                    });
            });
    }
    
    @Override
    public Uni<Empty> deleteProcessRequest(DeleteProcessRequestRequest request) {
        return repository.delete(request.getStorageId())
            .map(deleted -> {
                if (!deleted) {
                    throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription("ProcessRequest not found with id: " + request.getStorageId())
                    );
                }
                return Empty.getDefaultInstance();
            });
    }
    
    // Helper methods
    
    private StoredPipeDoc buildStoredPipeDoc(String storageId, PipeDoc doc, Map<String, String> metadata) {
        StoredPipeDoc.Builder builder = StoredPipeDoc.newBuilder()
            .setStorageId(storageId)
            .setDocument(doc);
        
        // Extract tags and description from metadata
        Map<String, String> tags = new HashMap<>();
        String description = "";
        
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey().equals("_description")) {
                description = entry.getValue();
            } else if (!entry.getKey().startsWith("_")) {
                tags.put(entry.getKey(), entry.getValue());
            }
        }
        
        builder.putAllTags(tags);
        builder.setDescription(description);
        
        // Set timestamps from metadata
        if (metadata.containsKey("_createdAt")) {
            builder.setCreatedAt(instantToTimestamp(Instant.parse(metadata.get("_createdAt"))));
        }
        if (metadata.containsKey("_updatedAt")) {
            builder.setUpdatedAt(instantToTimestamp(Instant.parse(metadata.get("_updatedAt"))));
        }
        
        return builder.build();
    }
    
    private StoredProcessRequest buildStoredProcessRequest(String storageId, ModuleProcessRequest request, Map<String, String> metadata) {
        StoredProcessRequest.Builder builder = StoredProcessRequest.newBuilder()
            .setStorageId(storageId)
            .setRequest(request);
        
        // Extract fields from metadata
        if (metadata.containsKey("_name")) {
            builder.setName(metadata.get("_name"));
        }
        if (metadata.containsKey("_description")) {
            builder.setDescription(metadata.get("_description"));
        }
        
        // Extract tags
        Map<String, String> tags = new HashMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!entry.getKey().startsWith("_") && 
                !entry.getKey().equals("documentId") &&
                !entry.getKey().equals("pipelineName") &&
                !entry.getKey().equals("stepName")) {
                tags.put(entry.getKey(), entry.getValue());
            }
        }
        builder.putAllTags(tags);
        
        // Set timestamps
        if (metadata.containsKey("_createdAt")) {
            builder.setCreatedAt(instantToTimestamp(Instant.parse(metadata.get("_createdAt"))));
        }
        if (metadata.containsKey("_updatedAt")) {
            builder.setUpdatedAt(instantToTimestamp(Instant.parse(metadata.get("_updatedAt"))));
        }
        
        return builder.build();
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
}