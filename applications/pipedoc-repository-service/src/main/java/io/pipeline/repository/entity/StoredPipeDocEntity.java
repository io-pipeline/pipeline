package io.pipeline.repository.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;
import java.util.Map;

@MongoEntity(collection = "pipe_docs")
public class StoredPipeDocEntity extends PanacheMongoEntity {
    // The 'id' field is inherited from PanacheMongoEntity as ObjectId
    
    // Store the raw protobuf bytes of the PipeDoc
    public byte[] documentData;
    
    // Metadata fields
    public Map<String, String> tags;
    public String description;
    public Instant createdAt;
    public Instant updatedAt;
    
    // Optional fields for search/filtering
    public String documentId; // The original PipeDoc.id for reference
    public String sourceUri; // From PipeDoc.sourceUri if available
    public String title; // From PipeDoc.title if available
}