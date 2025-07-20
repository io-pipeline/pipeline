package io.pipeline.module.echo;

import io.pipeline.common.service.ProcessingBufferManager;
import io.pipeline.common.util.ProcessingBuffer;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.grpc.GrpcClient;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test class for validating @ProcessingBuffered interceptor functionality in echo module.
 * This provides comprehensive tests for buffer access, statistics, and save triggers
 * using the echo service.
 */
@QuarkusTest
@TestProfile(ProcessingBufferEnabledTestProfile.class)
public class ProcessingBufferAccessTest {

    private static final Logger LOG = Logger.getLogger(ProcessingBufferAccessTest.class);

    @Inject
    ProcessingBufferManager bufferManager;

    @GrpcClient
    PipeStepProcessor echoService;

    @ConfigProperty(name = "processing.buffer.enabled")
    boolean bufferEnabled;

    @Test
    public void testBufferAccessAndCapture() {
        LOG.info("=== Testing Buffer Access and Capture in Echo Module ===");
        
        if (!bufferEnabled) {
            LOG.info("Buffer disabled - skipping buffer access test");
            return;
        }

        // Clear any existing buffers to start clean
        bufferManager.clearAllBuffers();
        
        // Verify starting state
        assertThat("Should start with no captured documents", 
                  bufferManager.getTotalCapturedDocuments(), is(equalTo(0)));
        assertThat("Should start with no active buffers", 
                  bufferManager.getActiveBufferKeys(), is(empty()));

        // Process a document to trigger buffer capture
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("echo-buffer-test-" + UUID.randomUUID().toString())
                .setTitle("Echo Buffer Test")
                .setBody("Testing buffer access and capture functionality in echo module")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("echo-buffer-test")
                        .setPipeStepName("echo-buffer-step")
                        .setStreamId("echo-stream")
                        .build())
                .build();

        LOG.infof("Processing document to trigger buffer capture: %s", testDoc.getId());

        ProcessResponse response = echoService.processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        // Verify normal processing worked
        assertThat("Processing should succeed", response.getSuccess(), is(true));
        assertThat("Should have output document", response.hasOutputDoc(), is(true));

        // Now verify buffer capture occurred
        assertThat("Should have captured 1 document", 
                  bufferManager.getTotalCapturedDocuments(), is(equalTo(1)));
        
        Set<String> activeKeys = bufferManager.getActiveBufferKeys();
        assertThat("Should have 1 active buffer", activeKeys, hasSize(1));
        assertThat("Buffer key should contain EchoServiceImpl", 
                  activeKeys, hasItem(containsString("EchoServiceImpl")));

        // Get the specific buffer and verify its contents
        String methodKey = activeKeys.iterator().next();
        ProcessingBuffer<PipeDoc> buffer = bufferManager.getBuffer(methodKey);
        assertThat("Buffer should exist for processData method", buffer, is(notNullValue()));
        assertThat("Buffer should contain 1 document", buffer.size(), is(equalTo(1)));

        // Verify the captured document
        var capturedDocs = buffer.snapshot();
        assertThat("Should have 1 captured document", capturedDocs, hasSize(1));
        PipeDoc capturedDoc = capturedDocs.get(0);
        assertThat("Captured document ID should match output", 
                  capturedDoc.getId(), is(equalTo(response.getOutputDoc().getId())));

        LOG.info("✅ Buffer access and capture verification completed!");
    }

    @Test 
    public void testBufferStatisticsAndManagement() {
        LOG.info("=== Testing Buffer Statistics and Management in Echo Module ===");
        
        if (!bufferEnabled) {
            LOG.info("Buffer disabled - skipping statistics test");
            return;
        }

        // Clear buffers and process multiple documents
        bufferManager.clearAllBuffers();
        
        // Process 3 documents
        for (int i = 0; i < 3; i++) {
            PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("echo-stats-test-" + i + "-" + UUID.randomUUID().toString())
                    .setTitle("Echo Statistics Test " + i)
                    .setBody("Document " + i + " for echo statistics testing")
                    .build();

            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(ServiceMetadata.newBuilder()
                            .setPipelineName("echo-stats-test")
                            .setStreamId("echo-stats-stream-" + i)
                            .build())
                    .build();

            ProcessResponse response = echoService.processData(request)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();

            assertThat(String.format("Document %d should process successfully", i), 
                      response.getSuccess(), is(true));
        }

        // Verify statistics
        assertThat("Should have captured 3 documents total", 
                  bufferManager.getTotalCapturedDocuments(), is(equalTo(3)));

        Map<String, Integer> stats = bufferManager.getBufferStatistics();
        assertThat("Should have statistics for 1 method", stats.size(), is(equalTo(1)));
        String actualMethodKey = stats.keySet().iterator().next();
        assertThat("Method key should contain EchoServiceImpl", 
                  actualMethodKey, containsString("EchoServiceImpl"));
        assertThat("Method buffer should contain 3 documents", 
                  stats.get(actualMethodKey), is(equalTo(3)));

        LOG.infof("Buffer statistics: %s", stats);
        LOG.info("✅ Buffer statistics and management test completed!");
    }

    @Test
    public void testBufferSaveTrigger() {
        LOG.info("=== Testing Buffer Save Trigger Capability in Echo Module ===");
        
        if (!bufferEnabled) {
            LOG.info("Buffer disabled - skipping save trigger test");
            return;
        }

        // Clear buffers and process a document
        bufferManager.clearAllBuffers();
        
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("echo-save-trigger-test-" + UUID.randomUUID().toString())
                .setTitle("Echo Save Trigger Test")
                .setBody("Testing save trigger functionality in echo module")
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("echo-save-trigger-test")
                        .setStreamId("echo-save-stream")
                        .build())
                .build();

        ProcessResponse response = echoService.processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Processing should succeed", response.getSuccess(), is(true));
        assertThat("Should have captured 1 document", 
                  bufferManager.getTotalCapturedDocuments(), is(equalTo(1)));

        // Test individual buffer save
        Set<String> saveActiveKeys = bufferManager.getActiveBufferKeys();
        String methodKey = saveActiveKeys.iterator().next();
        boolean saveResult = bufferManager.saveBuffer(methodKey);
        assertThat("Buffer save should succeed", saveResult, is(true));

        // Test save all buffers (should work even if already saved)
        bufferManager.saveAllBuffers();

        LOG.info("✅ Buffer save trigger test completed!");
        LOG.info("📁 Note: Files saved to configured directory - future tests can verify actual file output");
    }
}