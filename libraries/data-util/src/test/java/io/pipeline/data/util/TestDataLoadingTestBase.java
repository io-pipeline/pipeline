package io.pipeline.data.util;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for verifying ProtobufTestDataHelper loads data correctly.
 * This tests both from filesystem (unit test) and from JAR (integration test).
 */
public abstract class TestDataLoadingTestBase {

    /**
     * Get the ProtobufTestDataHelper instance.
     * Unit tests can inject it, integration tests should create it directly.
     */
    protected abstract ProtobufTestDataHelper getTestDataHelper();

    //@Test
    //@Test
    void testLoadSamplePipeDocuments() {
        Collection<PipeDoc> docs = getTestDataHelper().getSamplePipeDocuments();
        
        assertFalse(docs.isEmpty(), "Sample pipe documents should be loaded");
        assertTrue(docs.size() > 10, "Should have more than 10 sample pipe documents");
        
        // Verify document structure
        docs.stream().limit(5).forEach(doc -> {
            assertFalse(doc.getId().isEmpty());
            assertTrue(doc.hasBody());
            assertFalse(doc.getBody().isEmpty());
        });
        
        System.out.println("Loaded " + docs.size() + " sample pipe documents");
    }

    @Test
    void testLoadTikaPipeDocuments() {
        Collection<PipeDoc> docs = getTestDataHelper().getTikaPipeDocuments();
        
        assertFalse(docs.isEmpty(), "Tika pipe documents should be loaded");
        assertTrue(docs.size() > 10, "Should have more than 10 tika pipe documents");
        
        // Tika docs should have body content
        docs.stream().limit(5).forEach(doc -> {
            assertFalse(doc.getId().isEmpty());
            assertTrue(doc.hasBody());
            assertFalse(doc.getBody().isEmpty());
        });
        
        System.out.println("Loaded " + docs.size() + " tika pipe documents");
    }

    @Test
    void testLoadPipeStreams() {
        Collection<PipeStream> streams = getTestDataHelper().getPipeStreams();
        
        assertFalse(streams.isEmpty(), "Pipe streams should be loaded");
        assertTrue(streams.size() > 10, "Should have more than 10 pipe streams");
        
        // Verify stream structure
        streams.stream().limit(5).forEach(stream -> {
            assertFalse(stream.getStreamId().isEmpty());
            assertTrue(stream.hasDocument());
            assertFalse(stream.getDocument().getId().isEmpty());
        });
        
        System.out.println("Loaded " + streams.size() + " pipe streams");
    }

    @Test
    void testLoadTikaRequests() {
        Collection<PipeStream> requests = getTestDataHelper().getTikaRequestStreams();
        
        assertFalse(requests.isEmpty(), "Tika request streams should be loaded");
        
        // Tika requests should have documents with blobs
        requests.stream().limit(5).forEach(stream -> {
            assertTrue(stream.hasDocument());
            assertTrue(stream.getDocument().hasBlob());
        });
        
        System.out.println("Loaded " + requests.size() + " tika request streams");
    }

    @Test
    void testLoadTikaResponses() {
        Collection<PipeDoc> responses = getTestDataHelper().getTikaResponseDocuments();
        
        assertFalse(responses.isEmpty(), "Tika response documents should be loaded");
        
        // Tika responses should have extracted text
        responses.stream().limit(5).forEach(doc -> {
            assertTrue(doc.hasBody());
            assertFalse(doc.getBody().isEmpty());
        });
        
        System.out.println("Loaded " + responses.size() + " tika response documents");
    }

    @Test
    void testLoadChunkerDocuments() {
        Collection<PipeDoc> chunks = getTestDataHelper().getChunkerPipeDocuments();
        
        assertFalse(chunks.isEmpty(), "Chunker documents should be loaded");
        
        System.out.println("Loaded " + chunks.size() + " chunker documents");
    }

    @Test
    void testCachingBehavior() {
        // First call
        Collection<PipeDoc> firstCall = getTestDataHelper().getSamplePipeDocuments();
        
        // Second call should return cached instance
        Collection<PipeDoc> secondCall = getTestDataHelper().getSamplePipeDocuments();
        
        assertSame(firstCall, secondCall, "Cached collections should be the same instance");
    }

    @Test
    void testMapsArePopulated() {
        // Load documents
        Collection<PipeDoc> docs = getTestDataHelper().getSamplePipeDocuments();
        
        // Get map
        var docsMap = getTestDataHelper().getSamplePipeDocumentsMap();
        
        assertEquals(docs.size(), docsMap.size(), "Document map should contain all documents");
        
        // Verify mapping
        docs.stream().limit(5).forEach(doc -> {
            assertTrue(docsMap.containsKey(doc.getId()));
            assertEquals(doc, docsMap.get(doc.getId()));
        });
    }
}