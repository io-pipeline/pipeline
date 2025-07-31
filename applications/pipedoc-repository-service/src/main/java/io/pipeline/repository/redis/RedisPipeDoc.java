package io.pipeline.repository.redis;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis representation of a stored PipeDoc.
 * Uses claim check pattern - actual document data stored separately.
 */
public class RedisPipeDoc {
    
    private String id; // Redis key suffix
    private String payloadRef; // Reference to payload:xxx key containing protobuf bytes
    
    // Metadata fields
    private Map<String, String> tags = new HashMap<>();
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    
    // Searchable fields extracted from PipeDoc
    private String documentId; // The original PipeDoc.id
    private String sourceUri;
    private String title;
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getPayloadRef() {
        return payloadRef;
    }
    
    public void setPayloadRef(String payloadRef) {
        this.payloadRef = payloadRef;
    }
    
    public Map<String, String> getTags() {
        return tags;
    }
    
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
    
    public String getSourceUri() {
        return sourceUri;
    }
    
    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
}