package io.pipeline.repository.service;

import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.repository.entity.StoredPipeDocEntity;
import io.pipeline.repository.entity.StoredProcessRequestEntity;
import io.pipeline.repository.mapper.EntityMapper;
import io.pipeline.repository.v1.*;
import io.quarkus.grpc.GrpcService;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@GrpcService
public class PipeDocRepositoryServiceImpl extends PipeDocRepositoryGrpc.PipeDocRepositoryImplBase {
    
    private static final Logger LOG = Logger.getLogger(PipeDocRepositoryServiceImpl.class);
    
    @Inject
    EntityMapper mapper;
    
    @Override
    public void createPipeDoc(CreatePipeDocRequest request, StreamObserver<CreatePipeDocResponse> responseObserver) {
        try {
            StoredPipeDocEntity entity = new StoredPipeDocEntity();
            entity.documentData = request.getDocument().toByteArray();
            entity.tags = request.getTagsMap();
            entity.description = request.getDescription();
            entity.createdAt = Instant.now();
            entity.updatedAt = Instant.now();
            
            // Extract searchable fields from PipeDoc
            PipeDoc doc = request.getDocument();
            entity.documentId = doc.getId();
            entity.sourceUri = doc.hasSourceUri() ? doc.getSourceUri() : null;
            entity.title = doc.hasTitle() ? doc.getTitle() : null;
            
            entity.persist();
            
            StoredPipeDoc stored = mapper.toStoredPipeDoc(entity, request.getDocument());
            
            responseObserver.onNext(CreatePipeDocResponse.newBuilder()
                    .setStorageId(entity.id.toString())
                    .setStoredDocument(stored)
                    .build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOG.error("Error creating PipeDoc", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to create PipeDoc: " + e.getMessage())
                    .asException());
        }
    }
    
    @Override
    public void getPipeDoc(GetPipeDocRequest request, StreamObserver<StoredPipeDoc> responseObserver) {
        try {
            ObjectId id = new ObjectId(request.getStorageId());
            StoredPipeDocEntity entity = StoredPipeDocEntity.findById(id);
            
            if (entity == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("PipeDoc not found with id: " + request.getStorageId())
                        .asException());
                return;
            }
            
            PipeDoc doc = PipeDoc.parseFrom(entity.documentData);
            StoredPipeDoc stored = mapper.toStoredPipeDoc(entity, doc);
            
            responseObserver.onNext(stored);
            responseObserver.onCompleted();
            
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Error parsing PipeDoc data", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to parse stored document")
                    .asException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid storage ID format")
                    .asException());
        } catch (Exception e) {
            LOG.error("Error retrieving PipeDoc", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to retrieve PipeDoc: " + e.getMessage())
                    .asException());
        }
    }
    
    @Override
    public void updatePipeDoc(UpdatePipeDocRequest request, StreamObserver<StoredPipeDoc> responseObserver) {
        try {
            ObjectId id = new ObjectId(request.getStorageId());
            StoredPipeDocEntity entity = StoredPipeDocEntity.findById(id);
            
            if (entity == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("PipeDoc not found with id: " + request.getStorageId())
                        .asException());
                return;
            }
            
            // Update fields based on field mask or update all if no mask
            if (request.hasDocument()) {
                entity.documentData = request.getDocument().toByteArray();
                PipeDoc doc = request.getDocument();
                entity.documentId = doc.getId();
                entity.sourceUri = doc.hasSourceUri() ? doc.getSourceUri() : null;
                entity.title = doc.hasTitle() ? doc.getTitle() : null;
            }
            
            if (!request.getTagsMap().isEmpty()) {
                entity.tags = request.getTagsMap();
            }
            
            if (!request.getDescription().isEmpty()) {
                entity.description = request.getDescription();
            }
            
            entity.updatedAt = Instant.now();
            entity.update();
            
            PipeDoc doc = PipeDoc.parseFrom(entity.documentData);
            StoredPipeDoc stored = mapper.toStoredPipeDoc(entity, doc);
            
            responseObserver.onNext(stored);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOG.error("Error updating PipeDoc", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to update PipeDoc: " + e.getMessage())
                    .asException());
        }
    }
    
    @Override
    public void deletePipeDoc(DeletePipeDocRequest request, StreamObserver<Empty> responseObserver) {
        try {
            ObjectId id = new ObjectId(request.getStorageId());
            boolean deleted = StoredPipeDocEntity.deleteById(id);
            
            if (!deleted) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("PipeDoc not found with id: " + request.getStorageId())
                        .asException());
                return;
            }
            
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOG.error("Error deleting PipeDoc", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to delete PipeDoc: " + e.getMessage())
                    .asException());
        }
    }
    
    @Override
    public void listPipeDocs(ListPipeDocsRequest request, StreamObserver<ListPipeDocsResponse> responseObserver) {
        try {
            PanacheQuery<StoredPipeDocEntity> query;
            
            // Apply filter if provided
            if (!request.getFilter().isEmpty()) {
                query = StoredPipeDocEntity.find(request.getFilter());
            } else {
                query = StoredPipeDocEntity.findAll();
            }
            
            // Apply sorting
            if (!request.getOrderBy().isEmpty()) {
                // Note: Panache doesn't have a sort method on query, need to handle differently
                // For now, we'll skip sorting
            }
            
            // Apply pagination
            int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 50;
            int pageIndex = 0;
            
            if (!request.getPageToken().isEmpty()) {
                try {
                    pageIndex = Integer.parseInt(request.getPageToken());
                } catch (NumberFormatException e) {
                    // Invalid page token, start from beginning
                    pageIndex = 0;
                }
            }
            
            query = query.page(Page.of(pageIndex, pageSize));
            
            List<StoredPipeDocEntity> entities = query.list();
            List<StoredPipeDoc> results = new ArrayList<>();
            
            for (StoredPipeDocEntity entity : entities) {
                try {
                    PipeDoc doc = PipeDoc.parseFrom(entity.documentData);
                    results.add(mapper.toStoredPipeDoc(entity, doc));
                } catch (InvalidProtocolBufferException e) {
                    LOG.warn("Skipping corrupted document: " + entity.id, e);
                }
            }
            
            // Calculate next page token
            String nextPageToken = "";
            if (query.hasNextPage()) {
                nextPageToken = String.valueOf(pageIndex + 1);
            }
            
            ListPipeDocsResponse response = ListPipeDocsResponse.newBuilder()
                    .addAllDocuments(results)
                    .setNextPageToken(nextPageToken)
                    .setTotalCount((int) query.count())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOG.error("Error listing PipeDocs", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to list PipeDocs: " + e.getMessage())
                    .asException());
        }
    }
    
    @Override
    public StreamObserver<CreatePipeDocRequest> batchCreatePipeDocs(StreamObserver<BatchOperationResult> responseObserver) {
        return new StreamObserver<CreatePipeDocRequest>() {
            private final AtomicInteger counter = new AtomicInteger(0);
            
            @Override
            public void onNext(CreatePipeDocRequest request) {
                String operationId = "batch-" + counter.incrementAndGet();
                try {
                    StoredPipeDocEntity entity = new StoredPipeDocEntity();
                    entity.documentData = request.getDocument().toByteArray();
                    entity.tags = request.getTagsMap();
                    entity.description = request.getDescription();
                    entity.createdAt = Instant.now();
                    entity.updatedAt = Instant.now();
                    
                    PipeDoc doc = request.getDocument();
                    entity.documentId = doc.getId();
                    entity.sourceUri = doc.hasSourceUri() ? doc.getSourceUri() : null;
                    entity.title = doc.hasTitle() ? doc.getTitle() : null;
                    
                    entity.persist();
                    
                    responseObserver.onNext(BatchOperationResult.newBuilder()
                            .setOperationId(operationId)
                            .setSuccess(true)
                            .setResourceId(entity.id.toString())
                            .build());
                            
                } catch (Exception e) {
                    LOG.error("Error in batch create", e);
                    responseObserver.onNext(BatchOperationResult.newBuilder()
                            .setOperationId(operationId)
                            .setSuccess(false)
                            .setErrorMessage(e.getMessage())
                            .build());
                }
            }
            
            @Override
            public void onError(Throwable t) {
                LOG.error("Error in batch stream", t);
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Batch stream error: " + t.getMessage())
                        .asException());
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
    
    // TODO: Implement remaining methods for ModuleProcessRequest and Export/Import operations
}