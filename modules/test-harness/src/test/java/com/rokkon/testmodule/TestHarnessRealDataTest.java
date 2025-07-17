package com.rokkon.testmodule;

import com.google.protobuf.Empty;
import com.rokkon.pipeline.testing.harness.grpc.*;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.util.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import com.rokkon.pipeline.testing.util.UnifiedTestProfile;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for the TestHarness service using real test data.
 * Tests the TestHarness's ability to process various document types and simulate
 * different pipeline behaviors.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class TestHarnessRealDataTest {

    private static final Logger LOG = Logger.getLogger(TestHarnessRealDataTest.class);

    @GrpcClient
    TestHarness testHarness;

    @Inject
    ProtobufTestDataHelper testDataHelper;

    @BeforeEach
    void setup() {

        // Reset any simulation settings to ensure tests start with clean state
        TestCommand resetCommand = TestCommand.newBuilder()
                .setCommandId("reset-before-test")
                .setTimestamp(createTimestamp())
                .setSimulateScenario(SimulateScenarioCommand.newBuilder()
                        .setScenario(SimulateScenarioCommand.Scenario.NORMAL_PROCESSING)
                        .build())
                .build();

        testHarness.executeTest(resetCommand).await().atMost(Duration.ofSeconds(5));
    }

    private Stream<PipeDoc> sampleDocuments() {
        return testDataHelper.getSamplePipeDocuments().stream().limit(10);
    }

    private Stream<PipeDoc> tikaDocuments() {
        return testDataHelper.getTikaPipeDocuments().stream().limit(5);
    }

    @Test
    void testModuleStatusAfterProcessing() {
        // Get initial status
        ModuleStatus initialStatus = testHarness.getModuleStatus(Empty.getDefaultInstance())
                .await().atMost(Duration.ofSeconds(5));

        long initialProcessed = initialStatus.getDocumentsProcessed();

        // Process a document
        PipeDoc doc = testDataHelper.getSamplePipeDocuments().iterator().next();
        ProcessRequest processRequest = createProcessRequest(doc, "status-test");

        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(createTimestamp())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(processRequest)
                        .setExpectSuccess(true)
                        .setTimeoutMs(5000)
                        .build())
                .build();

        TestResult result = testHarness.executeTest(command)
                .await().atMost(Duration.ofSeconds(10));

        assertThat(result.getSuccess()).isTrue();

        // Get updated status
        ModuleStatus updatedStatus = testHarness.getModuleStatus(Empty.getDefaultInstance())
                .await().atMost(Duration.ofSeconds(5));

        assertThat(updatedStatus.getDocumentsProcessed()).isGreaterThan(initialProcessed);
    }

    @ParameterizedTest
    @MethodSource("sampleDocuments")
    void testProcessSampleDocuments(PipeDoc sampleDoc) {
        ProcessRequest processRequest = createProcessRequest(sampleDoc, "sample-test");

        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(createTimestamp())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(processRequest)
                        .setExpectSuccess(true)
                        .setTimeoutMs(5000)
                        .build())
                .build();

        TestResult result = testHarness.executeTest(command)
                .await().atMost(Duration.ofSeconds(10));

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessagesCount()).isGreaterThan(0);

        // Verify we got expected events
        boolean hasReceivedEvent = result.getEventsList().stream()
                .anyMatch(TestEvent::hasDocumentReceived);
        boolean hasProcessedEvent = result.getEventsList().stream()
                .anyMatch(TestEvent::hasDocumentProcessed);

        assertThat(hasReceivedEvent).isTrue();
        assertThat(hasProcessedEvent).isTrue();

        // Check the processed event details
        TestEvent processedEvent = result.getEventsList().stream()
                .filter(TestEvent::hasDocumentProcessed)
                .findFirst()
                .orElse(null);

        assertThat(processedEvent).isNotNull();
        assertThat(processedEvent.getDocumentProcessed().getSuccess()).isTrue();
        assertThat(processedEvent.getDocumentProcessed().getDocumentId()).isEqualTo(sampleDoc.getId());
    }

    @ParameterizedTest
    @MethodSource("tikaDocuments")
    void testProcessTikaDocuments(PipeDoc tikaDoc) {
        ProcessRequest processRequest = createProcessRequest(tikaDoc, "tika-test");

        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(createTimestamp())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(processRequest)
                        .setExpectSuccess(true)
                        .setTimeoutMs(10000) // Longer timeout for potentially larger docs
                        .build())
                .build();

        TestResult result = testHarness.executeTest(command)
                .await().atMost(Duration.ofSeconds(15));

        assertThat(result.getSuccess()).isTrue();

        // Verify processing metadata
        TestEvent processedEvent = result.getEventsList().stream()
                .filter(TestEvent::hasDocumentProcessed)
                .findFirst()
                .orElse(null);

        assertThat(processedEvent).isNotNull();
        assertThat(processedEvent.getDocumentProcessed().getProcessingTimeMs()).isGreaterThan(0);
    }

    @Test
    void testStreamProcessing() {
        // Test bidirectional streaming with multiple commands
        List<TestCommand> commands = new ArrayList<>();

        // Add health check command
        commands.add(TestCommand.newBuilder()
                .setCommandId("health-check-1")
                .setTimestamp(createTimestamp())
                .setCheckHealth(CheckHealthCommand.newBuilder()
                        .setIncludeDetails(true)
                        .build())
                .build());

        // Add document processing commands
        Collection<PipeDoc> docs = testDataHelper.getSamplePipeDocuments();
        int count = 0;
        for (PipeDoc doc : docs) {
            if (count++ >= 3) break; // Process first 3 documents

            ProcessRequest processRequest = createProcessRequest(doc, "stream-test");
            commands.add(TestCommand.newBuilder()
                    .setCommandId("process-" + count)
                    .setTimestamp(createTimestamp())
                    .setProcessDocument(ProcessDocumentCommand.newBuilder()
                            .setRequest(processRequest)
                            .setExpectSuccess(true)
                            .setTimeoutMs(5000)
                            .build())
                    .build());
        }

        // Send commands as a stream and collect events
        Multi<TestEvent> eventStream = testHarness.executeTestStream(Multi.createFrom().items(commands.stream()));

        AssertSubscriber<TestEvent> subscriber = eventStream
                .subscribe().withSubscriber(AssertSubscriber.create(20));

        subscriber.awaitCompletion(Duration.ofSeconds(30));

        List<TestEvent> events = subscriber.getItems();
        assertThat(events).isNotEmpty();

        // Verify we got events for each command
        long healthEvents = events.stream().filter(TestEvent::hasHealthCheck).count();
        long receivedEvents = events.stream().filter(TestEvent::hasDocumentReceived).count();
        long processedEvents = events.stream().filter(TestEvent::hasDocumentProcessed).count();

        assertThat(healthEvents).isGreaterThanOrEqualTo(1);
        assertThat(receivedEvents).isEqualTo(3);
        assertThat(processedEvents).isEqualTo(3);
    }

    @Test
    void testSimulateSlowProcessing() {
        PipeDoc doc = testDataHelper.getSamplePipeDocuments().iterator().next();
        ProcessRequest processRequest = createProcessRequest(doc, "slow-test");

        // First, configure the module for slow processing
        TestCommand configCommand = TestCommand.newBuilder()
                .setCommandId("config-slow")
                .setTimestamp(createTimestamp())
                .setSimulateScenario(SimulateScenarioCommand.newBuilder()
                        .setScenario(SimulateScenarioCommand.Scenario.SLOW_PROCESSING)
                        .setDurationMs(30000) // 30 seconds
                        .build())
                .build();

        TestResult configResult = testHarness.executeTest(configCommand)
                .await().atMost(Duration.ofSeconds(5));
        assertThat(configResult.getSuccess()).isTrue();

        // Now process a document - it should be slow
        TestCommand processCommand = TestCommand.newBuilder()
                .setCommandId("process-slow")
                .setTimestamp(createTimestamp())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(processRequest)
                        .setExpectSuccess(true)
                        .setTimeoutMs(15000)
                        .build())
                .build();

        long startTime = System.currentTimeMillis();
        TestResult result = testHarness.executeTest(processCommand)
                .await().atMost(Duration.ofSeconds(20));
        long duration = System.currentTimeMillis() - startTime;

        assertThat(result.getSuccess()).isTrue();
        assertThat(duration).isGreaterThan(2000); // Should take at least 2 seconds

        // Reset to normal processing
        TestCommand resetCommand = TestCommand.newBuilder()
                .setCommandId("reset-normal")
                .setTimestamp(createTimestamp())
                .setSimulateScenario(SimulateScenarioCommand.newBuilder()
                        .setScenario(SimulateScenarioCommand.Scenario.NORMAL_PROCESSING)
                        .build())
                .build();

        testHarness.executeTest(resetCommand).await().atMost(Duration.ofSeconds(5));
    }

    @Test
    void testSimulateRandomFailures() {
        // Configure random failures
        TestCommand configCommand = TestCommand.newBuilder()
                .setCommandId("config-failures")
                .setTimestamp(createTimestamp())
                .setSimulateScenario(SimulateScenarioCommand.newBuilder()
                        .setScenario(SimulateScenarioCommand.Scenario.RANDOM_FAILURES)
                        .setDurationMs(60000) // 1 minute
                        .build())
                .build();

        TestResult configResult = testHarness.executeTest(configCommand)
                .await().atMost(Duration.ofSeconds(5));
        assertThat(configResult.getSuccess()).isTrue();

        // Process multiple documents - some should fail
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Collection<PipeDoc> docs = testDataHelper.getSamplePipeDocuments();
        int processed = 0;
        for (PipeDoc doc : docs) {
            if (processed++ >= 10) break;

            ProcessRequest processRequest = createProcessRequest(doc, "failure-test");
            TestCommand command = TestCommand.newBuilder()
                    .setCommandId("process-" + processed)
                    .setTimestamp(createTimestamp())
                    .setProcessDocument(ProcessDocumentCommand.newBuilder()
                            .setRequest(processRequest)
                            .setExpectSuccess(false) // Don't require success
                            .setTimeoutMs(5000)
                            .build())
                    .build();

            TestResult result = testHarness.executeTest(command)
                    .await().atMost(Duration.ofSeconds(10));

            if (result.getSuccess()) {
                result.getEventsList().stream()
                        .filter(TestEvent::hasDocumentProcessed)
                        .findFirst()
                        .ifPresent(event -> {
                            if (event.getDocumentProcessed().getSuccess()) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        });
            }
        }

        // With random failures, we should have some of each
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(failureCount.get()).isGreaterThan(0);

        // Reset to normal
        TestCommand resetCommand = TestCommand.newBuilder()
                .setCommandId("reset-normal")
                .setTimestamp(createTimestamp())
                .setSimulateScenario(SimulateScenarioCommand.newBuilder()
                        .setScenario(SimulateScenarioCommand.Scenario.NORMAL_PROCESSING)
                        .build())
                .build();

        testHarness.executeTest(resetCommand).await().atMost(Duration.ofSeconds(5));
    }

    @Test
    void testBulkProcessingPerformance() {
        // Test processing a larger batch of documents
        Collection<PipeDoc> docs = testDataHelper.getSamplePipeDocuments();
        List<TestCommand> commands = new ArrayList<>();

        int count = 0;
        for (PipeDoc doc : docs) {
            if (count++ >= 50) break; // Process 50 documents

            ProcessRequest processRequest = createProcessRequest(doc, "bulk-test");
            commands.add(TestCommand.newBuilder()
                    .setCommandId("bulk-" + count)
                    .setTimestamp(createTimestamp())
                    .setProcessDocument(ProcessDocumentCommand.newBuilder()
                            .setRequest(processRequest)
                            .setExpectSuccess(true)
                            .setTimeoutMs(5000)
                            .build())
                    .build());
        }

        long startTime = System.currentTimeMillis();

        // Process all commands via streaming
        Multi<TestEvent> eventStream = testHarness.executeTestStream(Multi.createFrom().items(commands.stream()));

        AssertSubscriber<TestEvent> subscriber = eventStream
                .subscribe().withSubscriber(AssertSubscriber.create(200));

        subscriber.awaitCompletion(Duration.ofMinutes(2));

        long duration = System.currentTimeMillis() - startTime;

        List<TestEvent> events = subscriber.getItems();
        long processedCount = events.stream()
                .filter(TestEvent::hasDocumentProcessed)
                .filter(e -> e.getDocumentProcessed().getSuccess())
                .count();

        assertThat(processedCount).isEqualTo(50);

        double docsPerSecond = (processedCount * 1000.0) / duration;
        LOG.infof("Bulk processing performance: %.2f docs/sec (processed %d docs in %d ms)",
                docsPerSecond, processedCount, duration);
    }

    @Test
    void testChunkerDocuments() {
        // Test with chunked documents
        Collection<PipeDoc> chunkerDocs = testDataHelper.getChunkerPipeDocuments();
        assertThat(chunkerDocs).isNotEmpty();

        PipeDoc chunkedDoc = chunkerDocs.iterator().next();
        ProcessRequest processRequest = createProcessRequest(chunkedDoc, "chunker-test");

        TestCommand command = TestCommand.newBuilder()
                .setCommandId("chunker-test-1")
                .setTimestamp(createTimestamp())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(processRequest)
                        .setExpectSuccess(true)
                        .setTimeoutMs(5000)
                        .build())
                .build();

        TestResult result = testHarness.executeTest(command)
                .await().atMost(Duration.ofSeconds(10));

        assertThat(result.getSuccess()).isTrue();

        // Verify the document was processed with its chunk metadata intact
        TestEvent processedEvent = result.getEventsList().stream()
                .filter(TestEvent::hasDocumentProcessed)
                .findFirst()
                .orElse(null);

        assertThat(processedEvent).isNotNull();
        assertThat(processedEvent.getDocumentProcessed().getSuccess()).isTrue();
        if (processedEvent.getDocumentProcessed().hasProcessingMetadata()) {
            // If the processor added metadata, verify it
            assertThat(processedEvent.getDocumentProcessed().getProcessingMetadata().getFieldsCount())
                    .isGreaterThan(0);
        }
    }

    // Helper methods

    private ProcessRequest createProcessRequest(PipeDoc doc, String pipelineName) {
        return ProcessRequest.newBuilder()
                .setDocument(doc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName(pipelineName)
                        .setPipeStepName("test-harness")
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .putContextParams("test", "true")
                        .build())
                .build();
    }

    private com.google.protobuf.Timestamp createTimestamp() {
        long millis = System.currentTimeMillis();
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(millis / 1000)
                .setNanos((int) ((millis % 1000) * 1000000))
                .build();
    }
}
