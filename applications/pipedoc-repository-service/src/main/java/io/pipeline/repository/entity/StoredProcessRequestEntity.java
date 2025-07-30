package io.pipeline.repository.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;
import java.util.Map;

@MongoEntity(collection = "process_requests")
public class StoredProcessRequestEntity extends PanacheMongoEntity {
    // The 'id' field is inherited from PanacheMongoEntity as ObjectId
    
    // Store the raw protobuf bytes of the ModuleProcessRequest
    public byte[] requestData;
    
    // Human-readable fields
    public String name;
    public String description;
    public Map<String, String> tags;
    
    // Timestamps
    public Instant createdAt;
    public Instant updatedAt;
}