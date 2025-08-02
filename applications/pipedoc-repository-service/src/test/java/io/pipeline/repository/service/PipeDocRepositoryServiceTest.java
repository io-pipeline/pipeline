package io.pipeline.repository.service;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.repository.v1.*;
import io.pipeline.repository.filesystem.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class PipeDocRepositoryServiceTest {
    
    private static final Logger LOG = Logger.getLogger(PipeDocRepositoryServiceTest.class);
    
    @GrpcClient("test-pipedoc")
    MutinyPipeDocRepositoryGrpc.MutinyPipeDocRepositoryStub pipeDocRepository;
    
    @GrpcClient("test-filesystem")
    MutinyFilesystemServiceGrpc.MutinyFilesystemServiceStub filesystemService;
    
    @jakarta.inject.Inject
    io.pipeline.repository.config.DriveConfig driveConfig;
    
    @BeforeEach
    void cleanupBeforeTest() {
        // Clear any existing data before each test
        try {
            FormatFilesystemRequest formatRequest = FormatFilesystemRequest.newBuilder()
                .setDrive(driveConfig.defaultDrive())
                .setConfirmation("DELETE_FILESYSTEM_DATA")
                .build();
            
            FormatFilesystemResponse response = filesystemService.formatFilesystem(formatRequest)
                .await().indefinitely();
            
            LOG.debugf("Cleaned up filesystem before test: %s", response.getMessage());
        } catch (Exception e) {
            LOG.warnf("Failed to cleanup filesystem before test: %s", e.getMessage());
        }
    }
    
    @Test
    void testCreateAndGetPipeDoc() {
        // Given - Use unique IDs to avoid conflicts
        String uniqueId = "test-doc-" + System.currentTimeMillis();
        PipeDoc doc = PipeDoc.newBuilder()
            .setId(uniqueId)
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
        assertThat("Storage ID should not be null or empty", createResponse.getStorageId(), is(not(emptyString())));
        assertThat("Stored document should not be null", createResponse.getStoredDocument(), is(notNullValue()));
        assertThat("Stored document should have correct title", 
            createResponse.getStoredDocument().getDocument().getTitle(), is(equalTo("Test Document")));
        
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
        assertThat("Retrieved document ID should match", retrieved.getDocument().getId(), is(equalTo(uniqueId)));
        assertThat("Retrieved document title should match", retrieved.getDocument().getTitle(), is(equalTo("Test Document")));
        assertThat("Retrieved document body should match", retrieved.getDocument().getBody(), is(equalTo("Test content")));
        assertThat("Retrieved tags should contain category", retrieved.getTagsMap(), hasEntry("category", "test"));
        assertThat("Retrieved tags should contain environment", retrieved.getTagsMap(), hasEntry("environment", "testing"));
        assertThat("Retrieved description should match", retrieved.getDescription(), is(equalTo("Test document for unit testing")));
    }
    
    @Test
    void testUpdatePipeDoc() {
        // Given - Create a document first with unique ID
        String uniqueId = "update-test-" + System.currentTimeMillis();
        PipeDoc original = PipeDoc.newBuilder()
            .setId(uniqueId)
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
            .setId(uniqueId)
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
        // Given - Create a document with unique ID
        String uniqueId = "delete-test-" + System.currentTimeMillis();
        PipeDoc doc = PipeDoc.newBuilder()
            .setId(uniqueId)
            .setTitle("To Delete")
            .setBody("This document will be deleted")
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
        // Given - Create multiple documents with unique IDs
        String testPrefix = "list-test-" + System.currentTimeMillis() + "-";
        for (int i = 0; i < 3; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                .setId(testPrefix + i)
                .setTitle("List Test " + i)
                .setBody("Test document " + i + " for listing")
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
        assertThat("Should have at least 3 documents created in this test", 
            response.getDocumentsCount(), is(greaterThanOrEqualTo(3)));
        assertThat("Total count should be at least 3", 
            response.getTotalCount(), is(greaterThanOrEqualTo(3)));
        
        // Verify our test documents are in the list
        long testDocsCount = response.getDocumentsList().stream()
            .filter(doc -> doc.getDocument().getId().startsWith(testPrefix))
            .count();
        assertThat("Should find all 3 test documents in the list", 
            testDocsCount, is(equalTo(3L)));
    }
    
    @Test
    void testFormatRepository() {
        // Given - Create some test data
        String testPrefix = "format-test-" + System.currentTimeMillis() + "-";
        
        // Create PipeDocs
        for (int i = 0; i < 2; i++) {
            PipeDoc doc = PipeDoc.newBuilder()
                .setId(testPrefix + "doc-" + i)
                .setTitle("Format Test Doc " + i)
                .setBody("Document to be formatted")
                .build();
            
            pipeDocRepository.createPipeDoc(
                CreatePipeDocRequest.newBuilder()
                    .setDocument(doc)
                    .putTags("test", "format")
                    .build()
            ).await().indefinitely();
        }
        
        // When - Format with dry run first
        FormatRepositoryRequest dryRunRequest = FormatRepositoryRequest.newBuilder()
            .setConfirmation("DELETE_REPOSITORY_DATA")
            .setIncludeDocuments(true)
            .setIncludeRequests(false)
            .setDryRun(true)
            .build();
        
        FormatRepositoryResponse dryRunResponse = pipeDocRepository.formatRepository(dryRunRequest)
            .await().indefinitely();
        
        // Then - Verify dry run results
        assertThat("Dry run should succeed", dryRunResponse.getSuccess(), is(true));
        assertThat("Dry run should report at least 2 documents would be deleted", 
            dryRunResponse.getDocumentsDeleted(), is(greaterThanOrEqualTo(2)));
        assertThat("Dry run should report 0 requests would be deleted (we only asked for documents)", 
            dryRunResponse.getRequestsDeleted(), is(equalTo(0)));
        LOG.debugf("Dry run response: %s", dryRunResponse.getMessage());
        
        // Verify documents still exist after dry run
        ListPipeDocsResponse listAfterDryRun = pipeDocRepository.listPipeDocs(
            ListPipeDocsRequest.newBuilder().build()
        ).await().indefinitely();
        assertThat("Documents should still exist after dry run", 
            listAfterDryRun.getTotalCount(), is(greaterThanOrEqualTo(2)));
        
        // When - Actually format
        FormatRepositoryRequest formatRequest = FormatRepositoryRequest.newBuilder()
            .setConfirmation("DELETE_REPOSITORY_DATA")
            .setIncludeDocuments(true)
            .setIncludeRequests(true)
            .setDryRun(false)
            .build();
        
        FormatRepositoryResponse formatResponse = pipeDocRepository.formatRepository(formatRequest)
            .await().indefinitely();
        
        // Then - Verify format results
        assertThat("Format should succeed", formatResponse.getSuccess(), is(true));
        assertThat("Format should delete at least 2 documents", 
            formatResponse.getDocumentsDeleted(), is(greaterThanOrEqualTo(2)));
        LOG.debugf("Format response: %s", formatResponse.getMessage());
        
        // Verify repository is empty
        ListPipeDocsResponse listAfterFormat = pipeDocRepository.listPipeDocs(
            ListPipeDocsRequest.newBuilder().build()
        ).await().indefinitely();
        assertThat("No documents should exist after format", 
            listAfterFormat.getTotalCount(), is(equalTo(0)));
    }
}