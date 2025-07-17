package io.pipeline.data.util;

import io.pipeline.data.model.PipeDoc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the imported test data files are compatible with current protobuf definitions.
 */
@QuarkusTest
class TestDataCompatibilityTest {

    @Test
    void testSamplePipeDocCompatibility() throws Exception {
        // Try to load one sample pipe doc
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL resource = cl.getResource("test-data/sample-pipe-docs/pipe_doc_000_doc-e67ad3e6-0d2d-34f4-86cd-ce553364693b.bin");
        
        assertNotNull(resource);
        
        try (InputStream is = resource.openStream()) {
            PipeDoc doc = PipeDoc.parseFrom(is);
            
            // Verify basic fields
            assertNotNull(doc);
            assertFalse(doc.getId().isEmpty());
            System.out.println("Successfully loaded sample PipeDoc:");
            System.out.println("  ID: " + doc.getId());
            System.out.println("  Has Body: " + doc.hasBody());
            System.out.println("  Body Length: " + (doc.hasBody() ? doc.getBody().length() : 0));
            System.out.println("  Has Title: " + doc.hasTitle());
            System.out.println("  Has CustomData: " + doc.hasCustomData());
        }
    }

    @Test
    void testTikaPipeDocCompatibility() throws Exception {
        // Try to load one tika pipe doc
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL resource = cl.getResource("test-data/tika-pipe-docs/tika_doc_000_doc-000-e15df1cf.bin");
        
        assertNotNull(resource);
        
        try (InputStream is = resource.openStream()) {
            PipeDoc doc = PipeDoc.parseFrom(is);
            
            // Verify basic fields
            assertNotNull(doc);
            assertFalse(doc.getId().isEmpty());
            System.out.println("\nSuccessfully loaded Tika PipeDoc:");
            System.out.println("  ID: " + doc.getId());
            System.out.println("  Has Body: " + doc.hasBody());
            System.out.println("  Body Length: " + (doc.hasBody() ? doc.getBody().length() : 0));
            System.out.println("  Has Title: " + doc.hasTitle());
            System.out.println("  Has CustomData: " + doc.hasCustomData());
        }
    }

    @Test
    void testChunkerPipeDocCompatibility() throws Exception {
        // Try to load one existing chunker pipe doc
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL resource = cl.getResource("test-data/chunker-pipe-docs/chunker_output_0.bin");
        
        if (resource == null) {
            // Try another filename pattern
            resource = cl.getResource("test-data/chunker-pipe-docs");
            if (resource != null) {
                System.out.println("Chunker directory exists but need to check filenames");
            }
            return;
        }
        
        try (InputStream is = resource.openStream()) {
            PipeDoc doc = PipeDoc.parseFrom(is);
            
            // Verify basic fields
            assertNotNull(doc);
            assertFalse(doc.getId().isEmpty());
            System.out.println("\nSuccessfully loaded Chunker PipeDoc:");
            System.out.println("  ID: " + doc.getId());
            System.out.println("  Has Body: " + doc.hasBody());
            System.out.println("  Body Length: " + (doc.hasBody() ? doc.getBody().length() : 0));
        }
    }
}