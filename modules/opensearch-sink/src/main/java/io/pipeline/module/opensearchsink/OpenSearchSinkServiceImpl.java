package io.pipeline.module.opensearchsink;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.data.model.*;
import io.pipeline.data.module.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;
import org.jboss.logging.Logger;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.StringReader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the OpenSearch sink service.
 * This service indexes documents with embeddings into OpenSearch.
 * It supports dynamic field tracking, multiple chunking styles, and vector models.
 */
@Singleton
@GrpcService
@PipelineAutoRegister(
    moduleType = "opensearch",
    useHttpPort = true,
    metadata = {"category=sink", "type=vector-search"}
)
public class OpenSearchSinkServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger(OpenSearchSinkServiceImpl.class);
    
    private final OpenSearchClient openSearchClient;
    
    @Inject
    OpenSearchSettings settings;
    
    @ConfigProperty(name = "opensearch.default.index-prefix", defaultValue = "pipeline")
    String defaultIndexPrefix;
    
    @ConfigProperty(name = "opensearch.default.vector-dimension", defaultValue = "768")
    int defaultVectorDimension;
    
    public OpenSearchSinkServiceImpl(OpenSearchClient openSearchClient) {
        this.openSearchClient = openSearchClient;
    }
    
    @PostConstruct
    void init() {
        LOG.info("OpenSearchSinkServiceImpl initialized - ready to index embedded documents");
        LOG.infof("Default settings - index prefix: %s, vector dimension: %d", 
                 defaultIndexPrefix, defaultVectorDimension);
        LOG.infof("Using cluster: %s, pipeline: %s", 
                 settings.getClusterName(), settings.getPipelineName());
    }
    
    @Override
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                if (!request.hasDocument()) {
                    throw new IllegalArgumentException("No document provided in the request");
                }
                
                // Extract document with embeddings
                PipeDoc document = request.getDocument();
                ServiceMetadata metadata = request.getMetadata();
                String streamId = metadata.getStreamId();
                
                // Get the action type from the PipeStream context params or from the document itself
                ActionType actionType = ActionType.CREATE; // Default to CREATE
                
                // Check context params first
                String actionTypeStr = metadata.getContextParamsOrDefault("action_type", "");
                if (!actionTypeStr.isEmpty()) {
                    if (actionTypeStr.equalsIgnoreCase("DELETE")) {
                        actionType = ActionType.DELETE;
                    } else if (actionTypeStr.equalsIgnoreCase("UPDATE")) {
                        actionType = ActionType.UPDATE;
                    }
                } 
                // If not in context params, check if it's in the document metadata
                else if (document.getMetadataCount() > 0 && document.getMetadataMap().containsKey("action_type")) {
                    String docActionType = document.getMetadataMap().get("action_type");
                    if (docActionType.equalsIgnoreCase("DELETE")) {
                        actionType = ActionType.DELETE;
                    } else if (docActionType.equalsIgnoreCase("UPDATE")) {
                        actionType = ActionType.UPDATE;
                    }
                }
                
                LOG.infof("Processing document: %s with action: %s for stream: %s", 
                         document.getId(), actionType, streamId);
                
                // Handle different action types
                switch (actionType) {
                    case DELETE:
                        return handleDeleteDocument(document);
                        
                    case UPDATE:
                    case CREATE:
                        return handleCreateOrUpdateDocument(document);
                        
                    case NO_OP:
                    default:
                        LOG.infof("No operation (NO_OP) for document %s", document.getId());
                        return buildSuccessResponse(
                            String.format("No operation performed for document %s", document.getId()),
                            document
                        );
                }
                    
            } catch (Exception e) {
                LOG.errorf(e, "Failed to process document in OpenSearch");
                return createErrorResponse("OpenSearch processing failed: " + e.getMessage(), e);
            }
        });
    }
    
    private ModuleProcessResponse handleCreateOrUpdateDocument(PipeDoc document) throws Exception {
        // Check if we have semantic results to process
        if (document.getSemanticResultsCount() == 0) {
            LOG.warnf("Document %s has no semantic results (chunks) to index", document.getId());
            return buildSuccessResponse(
                "Document has no semantic results to index",
                document
            );
        }
        
        // Process each semantic result (chunked and embedded data)
        int totalIndexed = 0;
        for (SemanticProcessingResult result : document.getSemanticResultsList()) {
            if (result.getChunksCount() > 0) {
                String indexName = determineIndexName(document, result);
                
                // Check/create index with proper vector mapping
                ensureVectorIndex(indexName, result);
                
                // Index the chunks with embeddings
                int indexed = indexChunksWithEmbeddings(indexName, document, result);
                totalIndexed += indexed;
                
                LOG.infof("Indexed %d chunks to OpenSearch index %s", indexed, indexName);
            }
        }
        
        LOG.infof("Successfully indexed %d total chunks from document %s", 
                 totalIndexed, document.getId());
        
        return buildSuccessResponse(
            String.format("Successfully indexed %d chunks to OpenSearch", totalIndexed),
            document
        );
    }
    
    private ModuleProcessResponse handleDeleteDocument(PipeDoc document) throws Exception {
        LOG.infof("Handling DELETE operation for document: %s", document.getId());
        
        // For each semantic result, we need to delete all chunks
        int totalDeleted = 0;
        
        if (document.getSemanticResultsCount() > 0) {
            for (SemanticProcessingResult result : document.getSemanticResultsList()) {
                String indexName = determineIndexName(document, result);
                
                // Check if index exists
                ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
                BooleanResponse exists = openSearchClient.indices().exists(existsRequest);
                
                if (!exists.value()) {
                    LOG.warnf("Index %s does not exist, skipping deletion", indexName);
                    continue;
                }
                
                // Delete all chunks for this document
                List<BulkOperation> operations = new ArrayList<>();
                
                for (SemanticChunk chunk : result.getChunksList()) {
                    operations.add(
                        new BulkOperation.Builder()
                            .delete(DeleteOperation.of(d -> 
                                d.index(indexName)
                                 .id(chunk.getChunkId())
                            ))
                            .build()
                    );
                    totalDeleted++;
                }
                
                if (!operations.isEmpty()) {
                    BulkResponse bulkResponse = openSearchClient.bulk(
                        new BulkRequest.Builder().operations(operations).build()
                    );
                    
                    if (bulkResponse.errors()) {
                        LOG.warnf("Some bulk delete operations failed for index %s", indexName);
                        // Log the first few errors
                        bulkResponse.items().stream()
                            .filter(item -> item.error() != null)
                            .limit(5)
                            .forEach(item -> 
                                LOG.warnf("Error for id %s: %s", 
                                    item.id(), 
                                    item.error() != null ? item.error().reason() : "unknown"
                                )
                            );
                    }
                }
            }
        } else {
            // If no semantic results, try to delete by document ID across all indices
            // This is a fallback mechanism
            LOG.warnf("No semantic results found for document %s, attempting to delete by document ID", document.getId());
            
            // We would need to query for all chunks with this document ID
            // This is a simplified approach - in a real implementation, you might want to
            // search across indices for chunks with this document ID
        }
        
        return buildSuccessResponse(
            String.format("Successfully deleted %d chunks from OpenSearch", totalDeleted),
            document
        );
    }
    
    private String determineIndexName(PipeDoc document, SemanticProcessingResult result) {
        // Use result set name if available, otherwise document type
        String baseName = result.getResultSetName().isEmpty() ? 
            (document.getDocumentType().isEmpty() ? "documents" : document.getDocumentType()) :
            result.getResultSetName();
            
        return defaultIndexPrefix + "-" + baseName.toLowerCase().replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
    
    private void ensureVectorIndex(String indexName, SemanticProcessingResult result) throws Exception {
        // Check if index exists
        ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
        BooleanResponse exists = openSearchClient.indices().exists(existsRequest);
        
        if (!exists.value()) {
            LOG.infof("Creating new OpenSearch index: %s", indexName);
            
            // Determine vector dimension from first chunk embedding
            int vectorDimension = defaultVectorDimension;
            if (result.getChunksCount() > 0) {
                SemanticChunk firstChunk = result.getChunks(0);
                if (firstChunk.hasEmbeddingInfo() && firstChunk.getEmbeddingInfo().getVectorCount() > 0) {
                    vectorDimension = firstChunk.getEmbeddingInfo().getVectorCount();
                    LOG.infof("Detected vector dimension: %d from embeddings", vectorDimension);
                }
            }
            
            // Create index with vector mapping and dynamic field tracking
            createDynamicVectorIndex(indexName, vectorDimension);
            
            // Store schema metadata using settings service
            OpenSearchSettings.IndexSchema schema = new OpenSearchSettings.IndexSchema(
                indexName, vectorDimension, result.getEmbeddingConfigId(), this.settings.getDefaultVectorSpaceType());
            settings.storeIndexSchema(schema);
        }
    }
    
    private void createDynamicVectorIndex(String indexName, int vectorDimension) throws Exception {
        // Create index settings using the builder approach
        IndexSettings settings = new IndexSettings.Builder()
            .knn(true)
            .numberOfShards(1)
            .numberOfReplicas(1)
            .build();
            
        // Create the mapping for vector search with dynamic field tracking
        org.opensearch.client.opensearch._types.mapping.TypeMapping mapping = 
            new org.opensearch.client.opensearch._types.mapping.TypeMapping.Builder()
                .dynamic(DynamicMapping.True) // Use enum instead of string
                .properties("document_id", p -> p.keyword(k -> k))
                .properties("chunk_id", p -> p.keyword(k -> k))
                .properties("chunk_index", p -> p.integer(i -> i))
                .properties("chunk_text", p -> p.text(t -> t))
                .properties("result_id", p -> p.keyword(k -> k))
                // Add parent reference field for joins
                .properties("parent_id", p -> p.keyword(k -> k))
                // Add document type field to distinguish between documents and chunks
                .properties("doc_type", p -> p.keyword(k -> k))
                .properties("embedding", p -> p.knnVector(k -> k
                    .dimension(vectorDimension)
                    .method(method -> method
                        .name("hnsw")
                        .spaceType(this.settings.getDefaultVectorSpaceType())
                        .engine("lucene")
                    )
                ))
                .properties("vectors", p -> p.nested(n -> n
                    .dynamic(DynamicMapping.True) // Use enum instead of string
                ))
                .properties("metadata", p -> p.object(o -> o
                    .dynamic(DynamicMapping.True) // Use enum instead of string
                    .properties("source", mp -> mp.keyword(k -> k))
                    .properties("title", mp -> mp.text(t -> t))
                    .properties("document_type", mp -> mp.keyword(k -> k))
                    .properties("model_id", mp -> mp.keyword(k -> k))
                    .properties("chunking_style", mp -> mp.keyword(k -> k))
                    .properties("processing_timestamp", mp -> mp.date(d -> d))
                    .properties("cluster", mp -> mp.keyword(k -> k))
                    .properties("pipeline", mp -> mp.keyword(k -> k))
                ))
                .build();
                
        // Create the index request
        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder()
            .index(indexName)
            .settings(settings)
            .mappings(mapping)
            .build();
            
        // Create the index
        openSearchClient.indices().create(createIndexRequest);
        
        LOG.infof("Created dynamic vector index %s with %d dimensions", indexName, vectorDimension);
    }
    
    private int indexChunksWithEmbeddings(String indexName, PipeDoc document, 
                                         SemanticProcessingResult result) throws Exception {
        int chunkCount = 0;
        
        // Track chunking style for this result set
        String chunkingStyle = result.getResultSetName().isEmpty() ? 
            "default" : result.getResultSetName();
        
        List<BulkOperation> operations = new ArrayList<>();
        
        // Store document-level information separately to support future join queries
        // This allows for a parent-child relationship between documents and chunks
        if (result.getChunksCount() > 0) {
            Map<String, Object> docInfo = new HashMap<>();
            docInfo.put("document_id", document.getId());
            docInfo.put("title", document.getTitle());
            docInfo.put("source_uri", document.getSourceUri());
            docInfo.put("document_type", document.getDocumentType());
            docInfo.put("creation_date", document.hasCreationDate() ? document.getCreationDate().getSeconds() * 1000 : System.currentTimeMillis());
            docInfo.put("processing_timestamp", System.currentTimeMillis());
            // Add document type field to distinguish between documents and chunks
            docInfo.put("doc_type", "document");
            
            // Add document metadata
            Map<String, Object> docMetadata = new HashMap<>();
            if (document.getMetadataCount() > 0) {
                for (Map.Entry<String, String> entry : document.getMetadataMap().entrySet()) {
                    docMetadata.put(entry.getKey(), entry.getValue());
                }
            }
            docInfo.put("metadata", docMetadata);
            
            // Add to bulk operations - use document ID as the OpenSearch document ID
            String docIndexId = document.getId() + "_" + result.getResultId();
            operations.add(
                new BulkOperation.Builder()
                    .index(IndexOperation.of(io -> 
                        io.index(indexName)
                          .id(docIndexId)
                          .document(docInfo)
                    ))
                    .build()
            );
        }
        
        // Process each chunk
        for (SemanticChunk chunk : result.getChunksList()) {
            if (!chunk.hasEmbeddingInfo() || chunk.getEmbeddingInfo().getVectorCount() == 0) {
                LOG.warnf("Chunk %s has no embedding data, skipping", chunk.getChunkId());
                continue;
            }
            
            // Build document for indexing
            Map<String, Object> chunkDoc = new HashMap<>();
            
            // Add parent reference field for joins
            String parentId = document.getId() + "_" + result.getResultId();
            chunkDoc.put("parent_id", parentId);
            
            // Add document type field to distinguish between documents and chunks
            chunkDoc.put("doc_type", "chunk");
            
            // Add basic chunk information
            chunkDoc.put("document_id", document.getId());
            chunkDoc.put("chunk_id", chunk.getChunkId());
            chunkDoc.put("chunk_index", chunk.getChunkNumber());
            chunkDoc.put("chunk_text", chunk.getEmbeddingInfo().getTextContent());
            chunkDoc.put("result_id", result.getResultId());
            
            // Convert primary embedding vector
            List<Float> embedding = new ArrayList<>();
            for (float f : chunk.getEmbeddingInfo().getVectorList()) {
                embedding.add(f);
            }
            chunkDoc.put("embedding", embedding);
            
            // Handle multiple vector models by storing them in a nested structure
            Map<String, Object> vectors = new HashMap<>();
            String modelId = result.getEmbeddingConfigId();
            vectors.put(modelId, embedding);
            
            // If there are additional embeddings in namedEmbeddings, add them too
            if (document.getNamedEmbeddingsCount() > 0) {
                for (Map.Entry<String, Embedding> entry : document.getNamedEmbeddingsMap().entrySet()) {
                    String embeddingName = entry.getKey();
                    Embedding embeddingValue = entry.getValue();
                    
                    List<Float> additionalVector = new ArrayList<>();
                    for (float f : embeddingValue.getVectorList()) {
                        additionalVector.add(f);
                    }
                    
                    vectors.put(embeddingName, additionalVector);
                    
                    // Track this field for dynamic mapping
                    settings.trackField(indexName, "vectors." + embeddingName, "knn_vector");
                }
            }
            
            chunkDoc.put("vectors", vectors);
            
            // Add metadata with cluster and pipeline information
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", document.getSourceUri());
            metadata.put("title", document.getTitle());
            metadata.put("document_type", document.getDocumentType());
            metadata.put("model_id", modelId);
            metadata.put("chunking_style", chunkingStyle);
            metadata.put("processing_timestamp", System.currentTimeMillis());
            metadata.put("cluster", settings.getClusterName());
            metadata.put("pipeline", settings.getPipelineName());
            
            // Add any custom metadata from the document
            if (document.getMetadataCount() > 0) {
                for (Map.Entry<String, String> entry : document.getMetadataMap().entrySet()) {
                    String metadataKey = entry.getKey();
                    String metadataValue = entry.getValue();
                    
                    metadata.put(metadataKey, metadataValue);
                    
                    // Track this field for dynamic mapping
                    settings.trackField(indexName, "metadata." + metadataKey, "keyword");
                }
            }
            
            // Add chunk-specific metadata
            if (chunk.getMetadataCount() > 0) {
                for (Map.Entry<String, Value> entry : chunk.getMetadataMap().entrySet()) {
                    String metadataKey = entry.getKey();
                    Value metadataValue = entry.getValue();
                    
                    // Convert protobuf Value to Java object
                    Object value = null;
                    switch (metadataValue.getKindCase()) {
                        case STRING_VALUE:
                            value = metadataValue.getStringValue();
                            break;
                        case NUMBER_VALUE:
                            value = metadataValue.getNumberValue();
                            break;
                        case BOOL_VALUE:
                            value = metadataValue.getBoolValue();
                            break;
                        default:
                            value = metadataValue.toString();
                    }
                    
                    metadata.put("chunk_" + metadataKey, value);
                }
            }
            
            chunkDoc.put("metadata", metadata);
            
            // Add to bulk operations
            operations.add(
                new BulkOperation.Builder()
                    .index(IndexOperation.of(io -> 
                        io.index(indexName)
                          .id(chunk.getChunkId())
                          .document(chunkDoc)
                    ))
                    .build()
            );
            
            chunkCount++;
        }
        
        if (operations.size() > 0) {
            BulkResponse bulkResponse = openSearchClient.bulk(
                new BulkRequest.Builder().operations(operations).build()
            );
            
            if (bulkResponse.errors()) {
                LOG.warnf("Some bulk operations failed for index %s", indexName);
                // Log the first few errors
                bulkResponse.items().stream()
                    .filter(item -> item.error() != null)
                    .limit(5)
                    .forEach(item -> 
                        LOG.warnf("Error for id %s: %s", 
                            item.id(), 
                            item.error() != null ? item.error().reason() : "unknown"
                        )
                    );
            }
        }
        
        return chunkCount;
    }
    
    private ModuleProcessResponse buildSuccessResponse(String message, PipeDoc document) {
        ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder();
        responseBuilder.setSuccess(true);
        responseBuilder.setOutputDoc(document);
        responseBuilder.addProcessorLogs(message);
        return responseBuilder.build();
    }
    
    private ModuleProcessResponse createErrorResponse(String errorMessage, Throwable e) {
        ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder();
        responseBuilder.setSuccess(false);
        responseBuilder.addProcessorLogs(errorMessage);

        Struct.Builder errorDetailsBuilder = Struct.newBuilder();
        errorDetailsBuilder.putFields("error_message", Value.newBuilder().setStringValue(errorMessage).build());
        if (e != null) {
            errorDetailsBuilder.putFields("error_type", Value.newBuilder().setStringValue(e.getClass().getName()).build());
            if (e.getCause() != null) {
                errorDetailsBuilder.putFields("error_cause", Value.newBuilder().setStringValue(e.getCause().getMessage()).build());
            }
        }
        responseBuilder.setErrorDetails(errorDetailsBuilder.build());
        return responseBuilder.build();
    }
    
    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        LOG.info("OpenSearch sink service registration requested");
        
        ServiceRegistrationResponse.Builder responseBuilder = ServiceRegistrationResponse.newBuilder()
            .setModuleName("opensearch-sink")
            .setHealthCheckPassed(true)
            .setHealthCheckMessage("OpenSearch sink ready for vector indexing");
            
        // If test request is provided, perform health check
        if (request.hasTestRequest()) {
            LOG.info("Performing health check with test request");
            return processData(request.getTestRequest())
                .map(processResponse -> {
                    if (processResponse.getSuccess()) {
                        responseBuilder
                            .setHealthCheckPassed(true)
                            .setHealthCheckMessage("OpenSearch sink is healthy");
                    } else {
                        responseBuilder
                            .setHealthCheckPassed(false)
                            .setHealthCheckMessage("OpenSearch sink health check failed: " + 
                                String.join("; ", processResponse.getProcessorLogsList()));
                    }
                    return responseBuilder.build();
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.error("Health check failed with exception", error);
                    return responseBuilder
                        .setHealthCheckPassed(false)
                        .setHealthCheckMessage("Health check failed with exception: " + error.getMessage())
                        .build();
                });
        } else {
            // No test request provided, assume healthy
            return Uni.createFrom().item(responseBuilder.build());
        }
    }
    
    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        LOG.debug("TestProcessData called - proxying to processData");
        return processData(request);
    }
}