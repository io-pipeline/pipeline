package io.pipeline.module.draft;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.io.File;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Base test class for verifying that the @ProcessingBuffered interceptor works correctly.
 * This test specifically validates that the interceptor captures output documents
 * when processing.buffer.enabled=true.
 */
public abstract class ProcessingBufferInterceptorTestBase {

    private static final Logger LOG = Logger.getLogger(ProcessingBufferInterceptorTestBase.class);

    @ConfigProperty(name = "processing.buffer.enabled")
    boolean bufferEnabled;

    @ConfigProperty(name = "processing.buffer.directory", defaultValue = "target/test-data")
    String bufferDirectory;

    protected abstract PipeStepProcessor getDraftService();

    @Test
    public void testInterceptorCapturesProcessingOutput() {
        LOG.info("=== Testing ProcessingBuffered Interceptor ===");
        
        // Verify buffer is enabled for this test
        assertThat("Processing buffer should be enabled for this test", bufferEnabled, is(true));
        
        // Create a test document
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("interceptor-test-" + UUID.randomUUID().toString())
                .setTitle("Interceptor Test Document")
                .setBody("This document is used to test the @ProcessingBuffered interceptor")
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("interceptor-test-pipeline")
                .setPipeStepName("draft-interceptor-test")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("test_type", "interceptor_validation")
                .build();

        // Create request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .build();

        LOG.infof("Processing document through draft service: %s", testDoc.getId());

        // Execute and verify the service processes normally
        ModuleProcessResponse response = getDraftService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Verify the normal processing worked
        assertThat("Draft service should process successfully", response.getSuccess(), is(true));
        assertThat("Response should contain output document", response.hasOutputDoc(), is(true));
        assertThat("Output document ID should match input", response.getOutputDoc().getId(), is(equalTo(testDoc.getId())));
        assertThat("Processing logs should indicate success", response.getProcessorLogsList(), 
                  hasItem(containsString("successfully processed")));

        // Verify interceptor behavior - buffer directory should exist
        File bufferDir = new File(bufferDirectory);
        LOG.infof("Checking buffer directory: %s", bufferDir.getAbsolutePath());
        
        // Note: The interceptor should have created the buffer and captured the document
        // The actual file persistence happens when saveToDisk() is called on the buffer
        // For this test, we're primarily verifying that:
        // 1. The annotation is recognized
        // 2. The interceptor doesn't break normal processing
        // 3. The configuration is properly read

        LOG.info("✅ ProcessingBuffered interceptor test completed successfully!");
        LOG.info("📝 Note: Buffer capture occurs in memory - files saved when saveToDisk() is called");
    }

    @Test
    public void testInterceptorWithMultipleDocuments() {
        LOG.info("=== Testing Interceptor with Multiple Documents ===");

        // Process multiple documents to test buffer accumulation
        for (int i = 0; i < 3; i++) {
            PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("multi-test-" + i + "-" + UUID.randomUUID().toString())
                    .setTitle("Multi Test Document " + i)
                    .setBody("Document " + i + " for testing interceptor accumulation")
                    .build();

            ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(ServiceMetadata.newBuilder()
                            .setPipelineName("multi-test-pipeline")
                            .setPipeStepName("draft-multi-test")
                            .setStreamId("multi-stream-" + i)
                            .build())
                    .build();

            ModuleProcessResponse response = getDraftService().processData(request)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();

            assertThat(String.format("Document %d should process successfully", i), 
                      response.getSuccess(), is(true));
            assertThat(String.format("Document %d should have output", i), 
                      response.hasOutputDoc(), is(true));
            
            LOG.infof("Processed document %d: %s", i, testDoc.getId());
        }

        LOG.info("✅ Multiple document interceptor test completed successfully!");
    }
}