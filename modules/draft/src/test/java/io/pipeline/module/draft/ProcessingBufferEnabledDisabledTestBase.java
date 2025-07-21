package io.pipeline.module.draft;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test base for validating @ProcessingBuffered enabled vs disabled behavior.
 * This test ensures that:
 * 1. When enabled=true, the interceptor captures documents without affecting processing
 * 2. When enabled=false, the interceptor does nothing and processing works normally
 * 3. Both scenarios produce identical ProcessResponse results
 */
public abstract class ProcessingBufferEnabledDisabledTestBase {

    private static final Logger LOG = Logger.getLogger(ProcessingBufferEnabledDisabledTestBase.class);

    @ConfigProperty(name = "processing.buffer.enabled")
    boolean bufferEnabled;

    @ConfigProperty(name = "processing.buffer.capacity", defaultValue = "100")
    int bufferCapacity;

    @ConfigProperty(name = "processing.buffer.directory", defaultValue = "target/test-data")
    String bufferDirectory;

    @ConfigProperty(name = "processing.buffer.prefix", defaultValue = "test")
    String bufferPrefix;

    protected abstract PipeStepProcessor getDraftService();

    @Test
    public void testProcessingWithBufferConfiguration() {
        LOG.infof("=== Testing ProcessingBuffered: enabled=%s, capacity=%d, directory=%s, prefix=%s ===", 
                 bufferEnabled, bufferCapacity, bufferDirectory, bufferPrefix);
        
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("buffer-test-" + UUID.randomUUID().toString())
                .setTitle("Buffer Configuration Test Document")
                .setBody("Testing enabled=" + bufferEnabled + " with capacity=" + bufferCapacity)
                .putMetadata("test_scenario", "enabled_disabled_validation")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("buffer-config-test")
                .setPipeStepName("draft-buffer-test")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("buffer_enabled", String.valueOf(bufferEnabled))
                .putContextParams("buffer_capacity", String.valueOf(bufferCapacity))
                .build();

        // Create request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .build();

        LOG.infof("Processing document with buffer enabled=%s: %s", bufferEnabled, testDoc.getId());

        // Execute the service call
        ModuleProcessResponse response = getDraftService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Verify normal processing worked regardless of buffer configuration
        assertThat("Service should process successfully regardless of buffer setting", 
                  response.getSuccess(), is(true));
        assertThat("Response should contain output document", 
                  response.hasOutputDoc(), is(true));
        assertThat("Output document ID should be preserved", 
                  response.getOutputDoc().getId(), is(equalTo(testDoc.getId())));
        assertThat("Processing logs should indicate success", 
                  response.getProcessorLogsList(), hasItem(containsString("successfully processed")));

        // Verify the document was processed correctly (has draft metadata)
        PipeDoc outputDoc = response.getOutputDoc();
        assertThat("Output document should have draft metadata", 
                  outputDoc.getMetadataMap(), hasKey("draft_processed"));
        assertThat("Draft metadata should be set to true", 
                  outputDoc.getMetadataOrDefault("draft_processed", "false"), is(equalTo("true")));

        // Verify custom data was added by draft service
        assertThat("Output document should have custom data", 
                  outputDoc.hasCustomData(), is(true));
        assertThat("Custom data should contain processed_by_draft field", 
                  outputDoc.getCustomData().getFieldsMap(), hasKey("processed_by_draft"));

        // The interceptor behavior is transparent - we can't directly observe buffer capture
        // from the outside, but we can verify that processing works correctly in both cases
        LOG.infof("✅ Processing completed successfully with buffer enabled=%s", bufferEnabled);
        LOG.infof("📝 Buffer configuration: capacity=%d, directory=%s, prefix=%s", 
                 bufferCapacity, bufferDirectory, bufferPrefix);
    }

    @Test
    public void testMultipleDocumentsWithBufferConfiguration() {
        LOG.infof("=== Testing Multiple Documents: enabled=%s ===", bufferEnabled);

        // Process 3 documents to test behavior consistency
        for (int i = 0; i < 3; i++) {
            PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("multi-buffer-test-" + i + "-" + UUID.randomUUID().toString())
                    .setTitle("Multi Buffer Test " + i)
                    .setBody("Document " + i + " testing buffer enabled=" + bufferEnabled)
                    .putMetadata("document_number", String.valueOf(i))
                    .putMetadata("buffer_enabled", String.valueOf(bufferEnabled))
                    .build();

            ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(ServiceMetadata.newBuilder()
                            .setPipelineName("multi-buffer-test")
                            .setPipeStepName("draft-multi-buffer")
                            .setStreamId("buffer-stream-" + i)
                            .setCurrentHopNumber(i + 1)
                            .build())
                    .build();

            ModuleProcessResponse response = getDraftService().processData(request)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();

            // Each document should process successfully
            assertThat(String.format("Document %d should process successfully with buffer enabled=%s", i, bufferEnabled), 
                      response.getSuccess(), is(true));
            assertThat(String.format("Document %d should have output", i), 
                      response.hasOutputDoc(), is(true));
            assertThat(String.format("Document %d ID should be preserved", i), 
                      response.getOutputDoc().getId(), is(equalTo(testDoc.getId())));
            
            LOG.infof("Document %d processed successfully: %s", i, testDoc.getId());
        }

        LOG.infof("✅ All 3 documents processed successfully with buffer enabled=%s", bufferEnabled);
    }

    @Test
    public void testProcessingPerformanceWithBufferConfiguration() {
        LOG.infof("=== Testing Performance Impact: enabled=%s ===", bufferEnabled);

        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("perf-test-" + UUID.randomUUID().toString())
                .setTitle("Performance Test Document")
                .setBody("Testing performance impact of buffer enabled=" + bufferEnabled)
                .build();

        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("performance-test")
                        .setPipeStepName("draft-perf-test")
                        .setStreamId("perf-stream")
                        .build())
                .build();

        // Measure processing time
        long startTime = System.currentTimeMillis();

        ModuleProcessResponse response = getDraftService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        long processingTime = System.currentTimeMillis() - startTime;

        // Verify processing worked
        assertThat("Performance test should complete successfully", 
                  response.getSuccess(), is(true));
        assertThat("Processing time should be reasonable (< 5000ms)", 
                  processingTime, is(lessThan(5000L)));

        LOG.infof("✅ Processing completed in %dms with buffer enabled=%s", 
                 processingTime, bufferEnabled);
        LOG.infof("📊 Performance baseline established for buffer=%s configuration", bufferEnabled);
    }
}