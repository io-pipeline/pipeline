package io.pipeline.repository.service;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.pipeline.data.model.PipeDoc;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class RedisGenericRepositoryTest {

    @Inject
    GenericRepositoryService repository;

    @Test
    void testStoreAndRetrieveTypedMessage() {
        // Given
        PipeDoc doc = PipeDoc.newBuilder()
            .setId("test-doc-123")
            .setTitle("Test Document")
            .setDocumentType("test")
            .setBody("Test content")
            .build();
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("category", "test");
        metadata.put("author", "test-user");
        
        // When - Store the document
        String id = repository.store(doc, metadata)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Storage ID should not be null", id, is(notNullValue()));
        assertThat("Storage ID should be a valid UUID", id, matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        
        // Then - Retrieve and verify
        PipeDoc retrieved = repository.get(id, PipeDoc.class)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Retrieved PipeDoc should not be null", retrieved, is(notNullValue()));
        assertThat("Retrieved PipeDoc ID", retrieved.getId(), is(equalTo("test-doc-123")));
        assertThat("Retrieved PipeDoc title", retrieved.getTitle(), is(equalTo("Test Document")));
        assertThat("Retrieved PipeDoc type", retrieved.getDocumentType(), is(equalTo("test")));
        assertThat("Retrieved PipeDoc body", retrieved.getBody(), is(equalTo("Test content")));
        
        // Verify metadata
        Map<String, String> retrievedMetadata = repository.getMetadata(id)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Metadata should contain category", retrievedMetadata, hasEntry("category", "test"));
        assertThat("Metadata should contain author", retrievedMetadata, hasEntry("author", "test-user"));
        assertThat("Metadata should contain system fields", retrievedMetadata.keySet(), 
            hasItems("_id", "_typeUrl", "_size", "_createdAt", "_updatedAt"));
    }
    
    @Test
    void testStoreAndRetrieveAny() throws InvalidProtocolBufferException {
        // Given
        PipeDoc doc = PipeDoc.newBuilder()
            .setId("any-doc-456")
            .setTitle("Any Test Document")
            .build();
        
        Any anyDoc = Any.pack(doc);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "any-test");
        
        // When - Store as Any
        String id = repository.storeAny(anyDoc, metadata)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then - Retrieve as Any
        Any retrievedAny = repository.getAny(id)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Retrieved Any message should not be null", retrievedAny, is(notNullValue()));
        assertThat("Retrieved Any should contain PipeDoc", retrievedAny.is(PipeDoc.class), is(true));
        assertThat("Any type URL should match PipeDoc", retrievedAny.getTypeUrl(), 
            containsString("PipeDoc"));
        
        // Unpack and verify
        PipeDoc unpacked = retrievedAny.unpack(PipeDoc.class);
        assertThat("Unpacked PipeDoc should not be null", unpacked, is(notNullValue()));
        assertThat("Unpacked PipeDoc ID", unpacked.getId(), is(equalTo("any-doc-456")));
        assertThat("Unpacked PipeDoc title", unpacked.getTitle(), is(equalTo("Any Test Document")));
    }
    
    @Test
    void testListByType() {
        // Given - Store multiple documents
        PipeDoc doc1 = PipeDoc.newBuilder().setId("list-1").build();
        PipeDoc doc2 = PipeDoc.newBuilder().setId("list-2").build();
        
        repository.store(doc1, Map.of()).await().indefinitely();
        repository.store(doc2, Map.of()).await().indefinitely();
        
        // When - List by type
        List<String> ids = repository.listByType(PipeDoc.class)
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        // Then
        assertThat("List of IDs should not be null", ids, is(notNullValue()));
        assertThat("Should have at least 2 documents of this type", ids.size(), is(greaterThanOrEqualTo(2)));
    }
    
    @Test
    void testUpdateMessage() {
        // Given
        PipeDoc original = PipeDoc.newBuilder()
            .setId("update-test")
            .setTitle("Original Title")
            .build();
        
        String id = repository.store(original, Map.of("version", "1"))
            .await().indefinitely();
        
        // When - Update
        PipeDoc updated = PipeDoc.newBuilder()
            .setId("update-test")
            .setTitle("Updated Title")
            .setBody("New content")
            .build();
        
        boolean success = repository.update(id, updated, Map.of("version", "2"))
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();
        
        assertThat("Update operation should succeed", success, is(true));
        
        // Then - Verify update
        PipeDoc retrieved = repository.get(id, PipeDoc.class)
            .await().indefinitely();
        
        assertThat("Updated PipeDoc should not be null", retrieved, is(notNullValue()));
        assertThat("Updated title should match", retrieved.getTitle(), is(equalTo("Updated Title")));
        assertThat("Updated body should match", retrieved.getBody(), is(equalTo("New content")));
        
        Map<String, String> metadata = repository.getMetadata(id)
            .await().indefinitely();
        assertThat("Updated metadata should contain new version", metadata, hasEntry("version", "2"));
    }
    
    @Test
    void testDeleteMessage() {
        // Given
        PipeDoc doc = PipeDoc.newBuilder()
            .setId("delete-test")
            .build();
        
        String id = repository.store(doc, Map.of())
            .await().indefinitely();
        
        // Verify it exists
        assertThat("Document should exist before deletion", 
            repository.exists(id).await().indefinitely(), is(true));
        
        // When - Delete
        boolean deleted = repository.delete(id)
            .await().indefinitely();
        
        assertThat("Delete operation should succeed", deleted, is(true));
        
        // Then - Verify deletion
        assertThat("Document should not exist after deletion", 
            repository.exists(id).await().indefinitely(), is(false));
        assertThat("Getting deleted document should return null", 
            repository.get(id, PipeDoc.class).await().indefinitely(), is(nullValue()));
    }
    
    @Test
    void testBatchOperations() {
        // Given
        List<PipeDoc> docs = List.of(
            PipeDoc.newBuilder().setId("batch-1").setTitle("Doc 1").build(),
            PipeDoc.newBuilder().setId("batch-2").setTitle("Doc 2").build(),
            PipeDoc.newBuilder().setId("batch-3").setTitle("Doc 3").build()
        );
        
        // When - Batch store
        List<String> ids = repository.batchStore(docs, Map.of("batch", "true"))
            .await().indefinitely();
        
        assertThat("Batch store should return 3 IDs", ids, hasSize(3));
        assertThat("All IDs should be non-null", ids, everyItem(notNullValue()));
        
        // Then - Batch retrieve
        Map<String, Any> retrieved = repository.batchGetAny(ids)
            .await().indefinitely();
        
        assertThat("Batch retrieve should return 3 items", retrieved.entrySet(), hasSize(3));
        assertThat("All retrieved items should be PipeDoc messages", 
            retrieved.values().stream()
                .allMatch(any -> any.is(PipeDoc.class)), 
            is(true));
    }
}