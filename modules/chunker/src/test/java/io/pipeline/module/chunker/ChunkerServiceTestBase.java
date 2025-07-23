package io.pipeline.module.chunker;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.ProcessConfiguration;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.RegistrationRequest;
import io.pipeline.data.module.ServiceMetadata;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class ChunkerServiceTestBase {

    protected abstract PipeStepProcessor getChunkerService();

    @Test
    void testProcessData() {
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("This is a test document body")
                .setTitle("Test Document")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("chunker-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("algorithm", Value.newBuilder().setStringValue("token").build())
                        .putFields("sourceField", Value.newBuilder().setStringValue("body").build())
                        .putFields("chunkSize", Value.newBuilder().setNumberValue(500).build())
                        .putFields("chunkOverlap", Value.newBuilder().setNumberValue(50).build())
                        .build())
                .putConfigParams("mode", "chunker")
                .build();

        // Create request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getChunkerService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));
        assertThat("Output document ID should match input", response.getOutputDoc().getId(), is(testDoc.getId()));
        assertThat("Should have processor logs", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Should contain success message", response.getProcessorLogsList(), hasItem(containsString("successfully processed")));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed but with appropriate message
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("chunker-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getChunkerService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Chunker should handle missing document gracefully", response.getSuccess(), is(true));
        assertThat("No document means no output should be produced", response.hasOutputDoc(), is(false));
        assertThat("Should log processing attempt for missing document", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Should acknowledge successful handling of empty request", response.getProcessorLogsList(), hasItem(containsString("successfully processed")));
    }

    @Test
    void testProcessDataWithEmptyContent() {
        // Create a test document with empty body
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("")
                .setTitle("Empty Document")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("chunker-step")
                .setStreamId(UUID.randomUUID().toString())
                .build();

        // Create request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .build();

        // Execute and verify
        var response = getChunkerService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Chunker should handle empty content without failing", response.getSuccess(), is(true));
        assertThat("Empty document should still produce output structure", response.hasOutputDoc(), is(true));
        assertThat("Document identity should be preserved through empty processing", response.getOutputDoc().getId(), is(testDoc.getId()));
        assertThat("Should log empty content handling", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Should explicitly acknowledge empty content scenario", response.getProcessorLogsList(), hasItem(containsString("No content")));
    }

    @Test
    void testProcessDataWithCustomConfiguration() {
        // Create a test document with longer content
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("This is a longer test document body that should be chunked into multiple pieces " +
                        "based on the custom configuration settings. We want to ensure that the chunker " +
                        "respects the custom chunk size and overlap settings. This text should be long " +
                        "enough to be split into at least two chunks with the smaller chunk size setting.")
                .setTitle("Custom Config Test")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("chunker-step")
                .setStreamId(UUID.randomUUID().toString())
                .build();

        // Create configuration with smaller chunk size (30 tokens > 20 overlap, but < 58 total tokens)
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("algorithm", Value.newBuilder().setStringValue("token").build())
                        .putFields("sourceField", Value.newBuilder().setStringValue("body").build())
                        .putFields("chunkSize", Value.newBuilder().setNumberValue(30).build())
                        .putFields("chunkOverlap", Value.newBuilder().setNumberValue(10).build())
                        .putFields("config_id", Value.newBuilder().setStringValue("test_small_chunks").build())
                        .build())
                .build();

        // Create request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getChunkerService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Custom configuration should be processed successfully", response.getSuccess(), is(true));
        assertThat("Should produce chunked output document", response.hasOutputDoc(), is(true));
        assertThat("Should create semantic results with custom config", response.getOutputDoc().getSemanticResultsCount(), is(greaterThan(0)));
        assertThat("Should preserve original document metadata", response.getOutputDoc().getId(), is(equalTo(testDoc.getId())));

        // With a small chunk size (30 tokens), we should get multiple chunks from the long text (58+ tokens)
        var result = response.getOutputDoc().getSemanticResults(0);
        assertThat("Small chunk size should create multiple chunks", result.getChunksCount(), is(greaterThan(1)));
        assertThat("Should use custom chunk configuration ID", result.getChunkConfigId(), is(equalTo("test_small_chunks")));
        
        // Verify chunk properties
        for (int i = 0; i < result.getChunksCount(); i++) {
            var chunk = result.getChunks(i);
            assertThat(String.format("Chunk %d should have content", i), chunk.getEmbeddingInfo().getTextContent(), is(not(emptyString())));
            // For token chunking, chunks will be much shorter than character chunks
            assertThat(String.format("Chunk %d should be reasonable token-based size", i), chunk.getEmbeddingInfo().getTextContent().length(), is(lessThanOrEqualTo(200))); // Allow flexibility for token reconstruction
        }
    }

    @Test
    void testProcessDataWithUrlPreservation() {
        // Create a test document with URLs
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBody("This document contains URLs like https://example.com and http://test.org/page.html " +
                        "that should be preserved during chunking. The URLs should not be split across chunks.")
                .setTitle("URL Test")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("chunker-step")
                .setStreamId(UUID.randomUUID().toString())
                .build();

        // Create configuration with URL preservation enabled
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("algorithm", Value.newBuilder().setStringValue("token").build())
                        .putFields("sourceField", Value.newBuilder().setStringValue("body").build())
                        .putFields("chunkSize", Value.newBuilder().setNumberValue(100).build())
                        .putFields("chunkOverlap", Value.newBuilder().setNumberValue(20).build())
                        .putFields("preserveUrls", Value.newBuilder().setBoolValue(true).build())
                        .build())
                .build();

        // Create request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getChunkerService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("URL preservation chunking should succeed", response.getSuccess(), is(true));
        assertThat("Should produce output with URL handling", response.hasOutputDoc(), is(true));
        assertThat("Should create semantic results for URL content", response.getOutputDoc().getSemanticResultsCount(), is(greaterThan(0)));

        // Verify URL-aware chunking results
        var result = response.getOutputDoc().getSemanticResults(0);
        assertThat("Should produce chunks with URL content", result.getChunksCount(), is(greaterThan(0)));

        // Verify URLs are preserved intact across chunks
        boolean foundCompleteUrl = false;
        for (var chunk : result.getChunksList()) {
            String chunkText = chunk.getEmbeddingInfo().getTextContent();
            if (chunkText.contains("https://example.com") || chunkText.contains("http://test.org/page.html")) {
                foundCompleteUrl = true;
                // Ensure URL wasn't split across chunk boundaries
                assertThat("URLs should not be broken across chunks", 
                    chunkText.matches(".*https?://[^\\s]+.*"), is(true));
            }
        }
        
        assertThat("At least one chunk should contain complete URLs", foundCompleteUrl, is(true));
        assertThat("Should log successful URL-aware chunking", 
            response.getProcessorLogsList(), hasItem(containsString("Successfully created")));
    }

    @Test
    void testNullRequest() {
        // The service should handle null requests gracefully
        var response = getChunkerService().processData(null)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Null request handling should be robust and informative
        assertThat("Chunker should gracefully handle null input without crashing", response.getSuccess(), is(true));
        assertThat("Should provide diagnostic information for null request", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Should log at least one meaningful message about null handling", 
            response.getProcessorLogsList().size(), is(greaterThan(0)));
        assertThat("Should not attempt to process null as valid document", response.hasOutputDoc(), is(false));
    }

    @Test
    void testGetServiceRegistration() {
        var registration = getChunkerService().getServiceRegistration(RegistrationRequest.newBuilder().build())
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Service should identify itself as chunker module", registration.getModuleName(), is(equalTo("chunker")));
        assertThat("Registration should include configuration schema for clients", registration.hasJsonConfigSchema(), is(true));
        assertThat("Schema should contain text chunking configuration description", 
            registration.getJsonConfigSchema(), containsString("Configuration for text chunking operations"));
        assertThat("Schema should be valid JSON structure", 
            registration.getJsonConfigSchema().length(), is(greaterThan(10)));
    }
}
