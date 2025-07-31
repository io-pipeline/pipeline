package io.pipeline.repository.redis;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis representation of a filesystem node.
 * This is a POJO that will be serialized to/from Redis hashes.
 */
public class RedisFilesystemNode {
    
    // Core fields
    private String id;
    private String name;
    private String type; // "FOLDER" or "FILE"
    private String parentId;
    
    // Path for materialized path pattern (e.g., ",root_id,parent_id,")
    private String path;
    
    // Payload reference for claim check pattern
    private String payloadRef; // Reference to separate payload key
    private String payloadTypeUrl; // from google.protobuf.Any.getTypeUrl()
    
    // Visual and type information
    private String iconSvg;
    private String serviceType;
    private String payloadType;
    
    // File metadata
    private Long size;
    private String mimeType;
    
    // Additional metadata
    private Map<String, String> metadata = new HashMap<>();
    
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getPayloadRef() {
        return payloadRef;
    }
    
    public void setPayloadRef(String payloadRef) {
        this.payloadRef = payloadRef;
    }
    
    public String getPayloadTypeUrl() {
        return payloadTypeUrl;
    }
    
    public void setPayloadTypeUrl(String payloadTypeUrl) {
        this.payloadTypeUrl = payloadTypeUrl;
    }
    
    public String getIconSvg() {
        return iconSvg;
    }
    
    public void setIconSvg(String iconSvg) {
        this.iconSvg = iconSvg;
    }
    
    public String getServiceType() {
        return serviceType;
    }
    
    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }
    
    public String getPayloadType() {
        return payloadType;
    }
    
    public void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }
    
    public Long getSize() {
        return size;
    }
    
    public void setSize(Long size) {
        this.size = size;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
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
    
    /**
     * Get the full path as an array of ancestor IDs
     */
    public String[] getAncestorIds() {
        if (path == null || path.equals(",")) {
            return new String[0];
        }
        // Remove leading and trailing commas, then split
        String trimmed = path.substring(1, path.length() - 1);
        return trimmed.isEmpty() ? new String[0] : trimmed.split(",");
    }
}