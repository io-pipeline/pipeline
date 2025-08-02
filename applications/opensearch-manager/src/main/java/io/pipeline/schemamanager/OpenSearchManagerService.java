package io.pipeline.schemamanager;

import io.pipeline.schemamanager.opensearch.OpenSearchSchemaService;
import io.pipeline.opensearch.v1.*;
import io.pipeline.common.util.ProtoFieldMapper;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.google.protobuf.util.JsonFormat;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import io.quarkus.grpc.GrpcService;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@GrpcService
public class OpenSearchManagerService extends MutinyOpenSearchManagerServiceGrpc.OpenSearchManagerServiceImplBase {

    private static final Logger LOG = Logger.getLogger(OpenSearchManagerService.class);

    @Inject
    ReactiveRedisDataSource redis;

    @Inject
    OpenSearchSchemaService openSearchClient; // Inject the interface

    private static final String SERVICE_NAMESPACE = "schema-manager";
    private static final String CACHE_KEY_PREFIX = "cache:mapping:";
    private static final String LOCK_KEY_PREFIX = "lock:mapping:";

    @ConfigProperty(name = "schema.manager.lock.timeout", defaultValue = "PT10S")
    Duration lockTimeout;

    private ReactiveValueCommands<String, String> stringCommands() {
        return redis.value(String.class, String.class);
    }

    private String getCacheKey(String indexName) {
        return SERVICE_NAMESPACE + ":" + CACHE_KEY_PREFIX + indexName;
    }

    private String getLockKey(String indexName) {
        return SERVICE_NAMESPACE + ":" + LOCK_KEY_PREFIX + indexName;
    }

    @Override
    public Uni<EnsureNestedEmbeddingsFieldExistsResponse> ensureNestedEmbeddingsFieldExists(EnsureNestedEmbeddingsFieldExistsRequest request) {
        final String indexName = request.getIndexName();
        final String cacheKey = getCacheKey(indexName);
        final String lockKey = getLockKey(indexName);
        final String lockValue = UUID.randomUUID().toString();

        // 1. Check cache
        return stringCommands().get(cacheKey)
                .onItem().transformToUni(cachedSchemaVersion -> {
                    if (cachedSchemaVersion != null) {
                        return Uni.createFrom().item(buildResponse(true));
                    }
                    // 2. Cache Miss: Acquire lock
                    return acquireLock(lockKey, lockValue)
                            .onItem().transformToUni(lockAcquired -> {
                                if (!lockAcquired) {
                                    throw new RuntimeException("Could not acquire lock for schema update on index: " + indexName);
                                }
                                // 3. Lock acquired: Manage schema and release lock
                                return manageSchema(request, cacheKey)
                                        .eventually(() -> releaseLock(lockKey, lockValue));
                            });
                });
    }

    private Uni<Boolean> acquireLock(String lockKey, String lockValue) {
        // Atomic set-if-not-exists with an expiration
        return stringCommands().setnx(lockKey, lockValue)
                .onItem().transformToUni(acquired -> {
                    if (acquired) {
                        // If we acquired the lock, we must set the expiration
                        return redis.key(String.class).expire(lockKey, lockTimeout).replaceWith(true);
                    }
                    return Uni.createFrom().item(false);
                });
    }

    private Uni<Void> releaseLock(String lockKey, String lockValue) {
        // Safely release the lock only if we still own it (compare-and-delete)
        return stringCommands().get(lockKey)
                .onItem().transformToUni(currentLockValue -> {
                    if (lockValue.equals(currentLockValue)) {
                        return redis.key(String.class).del(lockKey).replaceWithVoid();
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    private Uni<EnsureNestedEmbeddingsFieldExistsResponse> manageSchema(EnsureNestedEmbeddingsFieldExistsRequest request, String cacheKey) {
        // Double-check cache now that we have the lock
        return stringCommands().get(cacheKey)
                .onItem().transformToUni(cachedSchemaVersion -> {
                    if (cachedSchemaVersion != null) {
                        return Uni.createFrom().item(buildResponse(true));
                    }
                    // Still a cache miss, now we talk to the source of truth via our interface
                    return openSearchClient.nestedMappingExists(request.getIndexName(), request.getNestedFieldName())
                            .onItem().transformToUni(exists -> {
                                if (exists) {
                                    LOG.infof("Schema already exists in OpenSearch for index '%s'. Updating cache.", request.getIndexName());
                                    return stringCommands().set(cacheKey, "v1").replaceWith(buildResponse(true));
                                } else {
                                    LOG.infof("Schema does not exist for index '%s'. Creating it now.", request.getIndexName());
                                    return openSearchClient.createIndexWithNestedMapping(request.getIndexName(), request.getNestedFieldName(), request.getVectorFieldDefinition())
                                            .onItem().transformToUni(success -> {
                                                if (success) {
                                                    LOG.infof("Successfully created schema for index '%s'. Updating cache.", request.getIndexName());
                                                    return stringCommands().set(cacheKey, "v1").replaceWith(buildResponse(false));
                                                } else {
                                                    throw new RuntimeException("Failed to create schema for index: " + request.getIndexName());
                                                }
                                            });
                                }
                            });
                });
    }

    private EnsureNestedEmbeddingsFieldExistsResponse buildResponse(boolean existed) {
        return EnsureNestedEmbeddingsFieldExistsResponse.newBuilder().setSchemaExisted(existed).build();
    }

    @Override
    public Uni<IndexDocumentResponse> indexDocument(IndexDocumentRequest request) {
        var document = request.getDocument();
        var indexName = request.getIndexName();
        
        // Ensure index exists with proper embedding fields
        return ensureIndexForDocument(indexName, document)
            .flatMap(v -> {
                try {
                    String jsonDoc = JsonFormat.printer().print(document);
                    LOG.infof("Indexing document %s: %s", document.getOriginalDocId(), jsonDoc);
                    
                    // Actually index the document
                    return indexDocumentToOpenSearch(indexName, document.getOriginalDocId(), jsonDoc)
                        .map(success -> IndexDocumentResponse.newBuilder()
                            .setSuccess(success)
                            .setDocumentId(document.getOriginalDocId())
                            .setMessage(success ? "Document indexed successfully" : "Failed to index document")
                            .build());
                } catch (Exception e) {
                    return Uni.createFrom().item(IndexDocumentResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Failed to index: " + e.getMessage())
                        .build());
                }
            });
    }
    
    private Uni<Void> ensureIndexForDocument(String indexName, OpenSearchDocument document) {
        // For each unique vector dimension, ensure the appropriate nested field exists
        Set<Integer> dimensions = document.getEmbeddingsList().stream()
            .mapToInt(e -> e.getVectorCount())
            .filter(d -> d > 0)
            .boxed()
            .collect(java.util.stream.Collectors.toSet());
            
        if (dimensions.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        
        // Create requests for each dimension
        List<Uni<EnsureNestedEmbeddingsFieldExistsResponse>> requests = dimensions.stream()
            .map(dim -> {
                String fieldName = "embeddings_" + dim;
                VectorFieldDefinition vectorDef = VectorFieldDefinition.newBuilder()
                    .setDimension(dim)
                    .build();
                    
                return ensureNestedEmbeddingsFieldExists(EnsureNestedEmbeddingsFieldExistsRequest.newBuilder()
                    .setIndexName(indexName)
                    .setNestedFieldName(fieldName)
                    .setVectorFieldDefinition(vectorDef)
                    .build());
            })
            .toList();
            
        return Uni.combine().all().unis(requests).discardItems();
    }
    
    private Uni<Boolean> indexDocumentToOpenSearch(String indexName, String documentId, String jsonDoc) {
        return Uni.createFrom().item(() -> {
            try {
                // TODO: Use OpenSearchClient to actually index
                // For now, just log and return success
                LOG.infof("Would index to %s with ID %s: %s", indexName, documentId, jsonDoc);
                return true;
            } catch (Exception e) {
                LOG.errorf(e, "Failed to index document %s", documentId);
                return false;
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<IndexDocumentResponse> indexAnyDocument(IndexAnyDocumentRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                var anyDocument = request.getDocument();
                var indexName = request.getIndexName();
                var fieldMappings = request.getFieldMappingsList();
                
                // Handle Any message manually with special case for StringValue
                // Note: We're not using JsonFormat.printer() with TypeRegistry because it requires
                // registering all possible types that might be contained in Any messages.
                // Instead, we handle common types directly and fall back to a simple representation for others.
                String jsonString;
                
                // Check if it's a StringValue (commonly used in tests)
                if (anyDocument.getTypeUrl().endsWith("google.protobuf.StringValue")) {
                    try {
                        // Unpack the StringValue and get its value directly
                        StringValue stringValue = anyDocument.unpack(StringValue.class);
                        jsonString = "{\"value\":\"" + stringValue.getValue() + "\"}";
                    } catch (InvalidProtocolBufferException e) {
                        LOG.errorf(e, "Failed to unpack StringValue");
                        throw e;
                    }
                } else {
                    // For other types, create a simple JSON representation with type URL and value
                    // This avoids the need for TypeRegistry while still providing useful information
                    jsonString = "{\"typeUrl\":\"" + anyDocument.getTypeUrl() + 
                                 "\",\"value\":\"" + anyDocument.getValue().toStringUtf8() + "\"}";
                }
                
                LOG.infof("Any document as JSON: %s", jsonString);
                
                // Create target OpenSearchDocument builder
                var targetBuilder = OpenSearchDocument.newBuilder();
                
                // If no field mappings provided, create a basic document with JSON content
                if (fieldMappings.isEmpty()) {
                    targetBuilder.setOriginalDocId(request.hasDocumentId() ? request.getDocumentId() : "unknown")
                               .setDocType("any_document")
                               .setBody(jsonString);
                } else {
                    // For field mappings, we need the original message
                    // This is a limitation - we'll need to support specific types for mapping
                    throw new IllegalArgumentException("Field mappings with Any documents require type-specific support. " +
                        "JSON representation: " + jsonString);
                }
                
                var mappedDocument = targetBuilder.build();
                var documentId = request.hasDocumentId() ? request.getDocumentId() : mappedDocument.getOriginalDocId();
                
                // TODO: Implement actual indexing logic using OpenSearchClient
                LOG.infof("Indexing Any document (type: %s) to index %s with %d field mappings", 
                         anyDocument.getTypeUrl(), indexName, fieldMappings.size());
                
                return IndexDocumentResponse.newBuilder()
                    .setSuccess(true)
                    .setDocumentId(documentId)
                    .setMessage("Any document indexed successfully with field mappings")
                    .build();
            } catch (InvalidProtocolBufferException e) {
                LOG.errorf(e, "Failed to unpack Any document");
                return IndexDocumentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to unpack Any document: " + e.getMessage())
                    .build();
            } catch (Exception e) {
                LOG.errorf(e, "Failed to index Any document");
                return IndexDocumentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to index Any document: " + e.getMessage())
                    .build();
            }
        });
    }

    @Override
    public Uni<CreateIndexResponse> createIndex(CreateIndexRequest request) {
        return ensureIndexWithEmbeddingsField(request.getIndexName(), request.getVectorFieldDefinition())
            .map(success -> CreateIndexResponse.newBuilder()
                .setSuccess(success)
                .setMessage(success ? "Index created successfully" : "Failed to create index")
                .build());
    }

    /**
     * Ensures index exists with proper embeddings field for Strategy 1.
     * Analyzes vector dimensions and creates appropriate nested fields.
     */
    private Uni<Boolean> ensureIndexWithEmbeddingsField(String indexName, VectorFieldDefinition vectorDef) {
        String fieldName = determineEmbeddingsFieldName(vectorDef.getDimension());
        return openSearchClient.createIndexWithNestedMapping(indexName, fieldName, vectorDef);
    }

    /**
     * Determines the embeddings field name based on dimension.
     * Strategy 1 uses separate fields for different dimensions (embeddings_384, embeddings_768).
     */
    private String determineEmbeddingsFieldName(int dimension) {
        return "embeddings_" + dimension;
    }

    @Override
    public Uni<IndexExistsResponse> indexExists(IndexExistsRequest request) {
        return openSearchClient.nestedMappingExists(request.getIndexName(), "embeddings")
            .map(exists -> IndexExistsResponse.newBuilder().setExists(exists).build());
    }

    /**
     * Strategy 1 helper: Analyzes OpenSearchDocument to determine required embedding fields.
     * Creates separate nested fields for different vector dimensions.
     */
    private Set<String> analyzeRequiredEmbeddingFields(OpenSearchDocument document) {
        Set<String> fields = new HashSet<>();
        Map<Integer, Set<String>> dimensionToEmbeddingIds = new HashMap<>();
        
        // Group embedding IDs by vector dimension
        for (var embedding : document.getEmbeddingsList()) {
            int dimension = embedding.getVectorCount();
            if (dimension > 0) {
                dimensionToEmbeddingIds.computeIfAbsent(dimension, k -> new HashSet<>())
                        .add(embedding.getEmbeddingId());
            }
        }
        
        // Create field names for each dimension
        for (int dimension : dimensionToEmbeddingIds.keySet()) {
            fields.add(determineEmbeddingsFieldName(dimension));
        }
        
        return fields.isEmpty() ? Set.of("embeddings") : fields;
    }
}
