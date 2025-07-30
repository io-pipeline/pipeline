package io.pipeline.repository.mapper;

import com.google.protobuf.Timestamp;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.repository.entity.StoredPipeDocEntity;
import io.pipeline.repository.entity.StoredProcessRequestEntity;
import io.pipeline.repository.v1.StoredPipeDoc;
import io.pipeline.repository.v1.StoredProcessRequest;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class EntityMapper {
    
    public StoredPipeDoc toStoredPipeDoc(StoredPipeDocEntity entity, PipeDoc document) {
        StoredPipeDoc.Builder builder = StoredPipeDoc.newBuilder()
                .setStorageId(entity.id.toString())
                .setDocument(document)
                .setCreatedAt(instantToTimestamp(entity.createdAt))
                .setUpdatedAt(instantToTimestamp(entity.updatedAt));
                
        if (entity.tags != null) {
            builder.putAllTags(entity.tags);
        }
        
        if (entity.description != null) {
            builder.setDescription(entity.description);
        }
        
        return builder.build();
    }
    
    public StoredProcessRequest toStoredProcessRequest(StoredProcessRequestEntity entity, ModuleProcessRequest request) {
        StoredProcessRequest.Builder builder = StoredProcessRequest.newBuilder()
                .setStorageId(entity.id.toString())
                .setRequest(request)
                .setCreatedAt(instantToTimestamp(entity.createdAt))
                .setUpdatedAt(instantToTimestamp(entity.updatedAt));
                
        if (entity.name != null) {
            builder.setName(entity.name);
        }
        
        if (entity.description != null) {
            builder.setDescription(entity.description);
        }
        
        if (entity.tags != null) {
            builder.putAllTags(entity.tags);
        }
        
        return builder.build();
    }
    
    private Timestamp instantToTimestamp(Instant instant) {
        if (instant == null) {
            instant = Instant.now();
        }
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
    
    public Instant timestampToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}