package io.pipeline.repository.redis;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis representation of a stored ModuleProcessRequest.
 * Uses claim check pattern - actual request data stored separately.
 */
public class RedisProcessRequest {
    
    private String id; // Redis key suffix
    private String payloadRef; // Reference to payload:xxx key containing protobuf bytes
    
    // Human-readable fields
    private String name;
    private String description;
    private Map<String, String> tags = new HashMap<>();
    
    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;
    
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, String> getTags() {
        return tags;
    }
    
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
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
}