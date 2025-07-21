package io.pipeline.module.opensearchsink;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.vertx.mutiny.ext.consul.ConsulClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for managing OpenSearch settings with caching support.
 * This class provides methods to store and retrieve settings related to OpenSearch indices,
 * including dynamic field tracking, cluster settings, and pipeline settings.
 */
@ApplicationScoped
public class OpenSearchSettings {
    
    private static final Logger LOG = Logger.getLogger(OpenSearchSettings.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // Cache for index schemas to avoid repeated Consul lookups
    private final Map<String, IndexSchema> indexSchemaCache = new ConcurrentHashMap<>();
    
    // Cache for dynamic fields to track new fields across requests
    private final Map<String, Map<String, String>> dynamicFieldsCache = new ConcurrentHashMap<>();
    
    @ConfigProperty(name = "opensearch.default.index-prefix")
    String defaultIndexPrefix;
    
    @ConfigProperty(name = "opensearch.default.vector-dimension")
    int defaultVectorDimension;
    
    @ConfigProperty(name = "opensearch.cluster.name", defaultValue = "opensearch-cluster")
    String clusterName;
    
    @ConfigProperty(name = "opensearch.pipeline.name", defaultValue = "default-pipeline")
    String pipelineName;
    
    @Inject
    ConsulClient consulClient;
    
    /**
     * Get the index schema for the specified index name.
     * This method is cached using Quarkus cache to improve performance.
     * 
     * @param indexName The name of the index
     * @return The index schema
     */
    @CacheResult(cacheName = "index-schema-cache")
    public IndexSchema getIndexSchema(String indexName) {
        LOG.debugf("Getting index schema for %s", indexName);
        
        // Check local cache first
        if (indexSchemaCache.containsKey(indexName)) {
            LOG.debugf("Found index schema in local cache for %s", indexName);
            return indexSchemaCache.get(indexName);
        }
        
        // Try to get from Consul
        try {
            String metadataKey = "opensearch/schemas/" + indexName;
            var keyValue = consulClient.getValue(metadataKey)
                .await().indefinitely();
            String schemaJson = keyValue != null ? keyValue.getValue() : null;
                
            if (schemaJson != null && !schemaJson.isEmpty()) {
                LOG.debugf("Found index schema in Consul for %s", indexName);
                IndexSchema schema = OBJECT_MAPPER.readValue(schemaJson, IndexSchema.class);
                indexSchemaCache.put(indexName, schema);
                return schema;
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to get schema from Consul for index %s", indexName);
        }
        
        // Create a new schema if not found
        LOG.debugf("Creating new index schema for %s", indexName);
        IndexSchema schema = new IndexSchema(indexName, defaultVectorDimension, "default", "cosinesimilarity");
        indexSchemaCache.put(indexName, schema);
        return schema;
    }
    
    /**
     * Store the index schema in Consul and update the local cache.
     * 
     * @param schema The index schema to store
     */
    public void storeIndexSchema(IndexSchema schema) {
        try {
            String metadataKey = "opensearch/schemas/" + schema.getIndexName();
            String schemaJson = OBJECT_MAPPER.writeValueAsString(schema);
            
            // Store in Consul
            consulClient.putValue(metadataKey, schemaJson)
                .await().indefinitely();
                
            // Update local cache
            indexSchemaCache.put(schema.getIndexName(), schema);
            
            LOG.debugf("Stored schema metadata in Consul: %s", metadataKey);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to store schema metadata in Consul for index %s", schema.getIndexName());
        }
    }
    
    /**
     * Track a new field for the specified index.
     * This method adds the field to the dynamic fields cache.
     * 
     * @param indexName The name of the index
     * @param fieldName The name of the field
     * @param fieldType The type of the field
     */
    public void trackField(String indexName, String fieldName, String fieldType) {
        Map<String, String> fields = dynamicFieldsCache.computeIfAbsent(indexName, k -> new ConcurrentHashMap<>());
        fields.put(fieldName, fieldType);
        LOG.debugf("Tracked new field %s of type %s for index %s", fieldName, fieldType, indexName);
    }
    
    /**
     * Get all tracked fields for the specified index.
     * 
     * @param indexName The name of the index
     * @return A map of field names to field types
     */
    public Map<String, String> getTrackedFields(String indexName) {
        return dynamicFieldsCache.getOrDefault(indexName, new HashMap<>());
    }
    
    /**
     * Get the cluster name.
     * 
     * @return The cluster name
     */
    public String getClusterName() {
        return clusterName;
    }
    
    /**
     * Get the pipeline name.
     * 
     * @return The pipeline name
     */
    public String getPipelineName() {
        return pipelineName;
    }
    
    /**
     * Class representing an OpenSearch index schema.
     */
    public static class IndexSchema {
        private String indexName;
        private int vectorDimension;
        private String modelId;
        private String spaceType;
        private long createdTimestamp;
        
        // Default constructor for Jackson
        public IndexSchema() {
        }
        
        public IndexSchema(String indexName, int vectorDimension, String modelId, String spaceType) {
            this.indexName = indexName;
            this.vectorDimension = vectorDimension;
            this.modelId = modelId;
            this.spaceType = spaceType;
            this.createdTimestamp = System.currentTimeMillis();
        }
        
        public String getIndexName() {
            return indexName;
        }
        
        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }
        
        public int getVectorDimension() {
            return vectorDimension;
        }
        
        public void setVectorDimension(int vectorDimension) {
            this.vectorDimension = vectorDimension;
        }
        
        public String getModelId() {
            return modelId;
        }
        
        public void setModelId(String modelId) {
            this.modelId = modelId;
        }
        
        public String getSpaceType() {
            return spaceType;
        }
        
        public void setSpaceType(String spaceType) {
            this.spaceType = spaceType;
        }
        
        public long getCreatedTimestamp() {
            return createdTimestamp;
        }
        
        public void setCreatedTimestamp(long createdTimestamp) {
            this.createdTimestamp = createdTimestamp;
        }
    }
}