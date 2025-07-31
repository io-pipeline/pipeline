package io.pipeline.repository.service;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.repository.v1.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class PipeDocRepositoryServiceTest {
    
    @GrpcClient("test-pipedoc")
    MutinyPipeDocRepositoryGrpc.MutinyPipeDocRepositoryStub pipeDocRepository;
    
    @Test
    void testCreateAndGetPipeDoc() {
        // Given
        PipeDoc doc = PipeDoc.newBuilder()
            .setId("test-doc-123")
            .setTitle("Test Document")
            .setDocumentType("test")
            .setBody("Test content")
            .build();
        
        Map<String, String> tags = new HashMap<>();
        tags.put("category", "test");
        tags.put("environment", "testing");
        
        CreatePipeDocRequest createRequest = CreatePipeDocRequest.newBuilder()
            .setDocument(doc)
            .putAllTags(tags)
            .setDescription("Test document for unit testing")
            .build();
        
        // When - Create the document
        CreatePipeDocResponse createResponse = pipeDocRepository.createPipeDoc(createRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Create response should not be null", createResponse, is(notNullValue()));
        assertThat("Storage ID should not be null", createResponse.getStorageId(), is(notNullValue()));
        assertThat("Storage ID should be a valid UUID", createResponse.getStorageId(), 
            matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertThat("Stored document should not be null", createResponse.getStoredDocument(), is(notNullValue()));
        
        String storageId = createResponse.getStorageId();
        
        // Then - Retrieve it
        GetPipeDocRequest getRequest = GetPipeDocRequest.newBuilder()
            .setStorageId(storageId)
            .build();
        
        StoredPipeDoc retrieved = pipeDocRepository.getPipeDoc(getRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Retrieved document should not be null", retrieved, is(notNullValue()));
        assertThat("Retrieved storage ID should match", retrieved.getStorageId(), is(equalTo(storageId)));
        assertThat("Retrieved document ID should match", retrieved.getDocument().getId(), is(equalTo("test-doc-123")));
        assertThat("Retrieved document title should match", retrieved.getDocument().getTitle(), is(equalTo("Test Document")));
        assertThat("Retrieved tags should contain category", retrieved.getTagsMap(), hasEntry("category", "test"));
        assertThat("Retrieved description should match", retrieved.getDescription(), is(equalTo("Test document for unit testing")));
    }
    
    @Test
    void testUpdatePipeDoc() {
        // Given - Create a document first
        PipeDoc original = PipeDoc.newBuilder()
            .setId("update-test")
            .setTitle("Original Title")
            .setBody("Original content")
            .build();
        
        CreatePipeDocResponse createResponse = pipeDocRepository.createPipeDoc(
            CreatePipeDocRequest.newBuilder()
                .setDocument(original)
                .setDescription("Original description")
                .build()
        ).await().indefinitely();
        
        String storageId = createResponse.getStorageId();
        
        // When - Update it
        PipeDoc updated = PipeDoc.newBuilder()
            .setId("update-test")
            .setTitle("Updated Title")
            .setBody("Updated content")
            .build();
        
        UpdatePipeDocRequest updateRequest = UpdatePipeDocRequest.newBuilder()
            .setStorageId(storageId)
            .setDocument(updated)
            .setDescription("Updated description")
            .putTags("version", "2")
            .build();
        
        StoredPipeDoc result = pipeDocRepository.updatePipeDoc(updateRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then
        assertThat("Update result should not be null", result, is(notNullValue()));
        assertThat("Updated document title should match", result.getDocument().getTitle(), is(equalTo("Updated Title")));
        assertThat("Updated document body should match", result.getDocument().getBody(), is(equalTo("Updated content")));
        assertThat("Updated description should match", result.getDescription(), is(equalTo("Updated description")));
        assertThat("Updated tags should contain version", result.getTagsMap(), hasEntry("version", "2"));
    }
    
    @Test
    void testDeletePipeDoc() {
        // Given - Create a document
        PipeDoc doc = PipeDoc.newBuilder()
            .setId("delete-test")
            .setTitle("To Delete")
            .build();
        
        CreatePipeDocResponse createResponse = pipeDocRepository.createPipeDoc(
            CreatePipeDocRequest.newBuilder()
                .setDocument(doc)
                .build()
        ).await().indefinitely();
        
        String storageId = createResponse.getStorageId();
        
        // When - Delete it
        DeletePipeDocRequest deleteRequest = DeletePipeDocRequest.newBuilder()
            .setStorageId(storageId)
            .build();
        
        pipeDocRepository.deletePipeDoc(deleteRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted();
        
        // Then - Verify it's gone
        GetPipeDocRequest getRequest = GetPipeDocRequest.newBuilder()
            .setStorageId(storageId)
            .build();
        
        pipeDocRepository.getPipeDoc(getRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitFailure()
            .assertFailedWith(Exception.class);
    }
    
    @Test
    void testListPipeDocs() {
        // Given - Create multiple documents
        for (int i = 0; i < 3; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                .setId("list-test-" + i)
                .setTitle("List Test " + i)
                .build();
            
            pipeDocRepository.createPipeDoc(
                CreatePipeDocRequest.newBuilder()
                    .setDocument(doc)
                    .putTags("type", "list-test")
                    .build()
            ).await().indefinitely();
        }
        
        // When - List documents
        ListPipeDocsRequest listRequest = ListPipeDocsRequest.newBuilder()
            .setPageSize(10)
            .build();
        
        ListPipeDocsResponse response = pipeDocRepository.listPipeDocs(listRequest)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then
        assertThat("List response should not be null", response, is(notNullValue()));
        assertThat("Should have at least 3 documents", response.getDocumentsCount(), is(greaterThanOrEqualTo(3)));
        assertThat("Total count should be at least 3", response.getTotalCount(), is(greaterThanOrEqualTo(3)));
    }
}