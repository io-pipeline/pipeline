package com.rokkon.testmodule;

import com.rokkon.pipeline.testing.harness.grpc.*;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.sdk.ProcessConfiguration;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.util.ProtobufTestDataHelper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test class for TestHarness service testing.
 * This abstract class can be extended by both unit tests (@QuarkusTest) 
 * and integration tests (@QuarkusIntegrationTest).
 */
public abstract class TestHarnessTestBase {

    protected abstract TestHarness getTestHarness();
    
    protected abstract ProtobufTestDataHelper getTestDataHelper();

    @Test
    void testBasicHealthCheck() {
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(createTimestamp())
                .setCheckHealth(CheckHealthCommand.newBuilder()
                        .setIncludeDetails(true)
                        .build())
                .build();
        
        TestResult result = getTestHarness().executeTest(command)
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getEventsCount()).isGreaterThan(0);
        
        TestEvent healthEvent = findEventByType(result, TestEvent::hasHealthCheck);
        assertThat(healthEvent).isNotNull();
        assertThat(healthEvent.getHealthCheck().getStatus())
                .isEqualTo(HealthCheckEvent.HealthStatus.HEALTHY);
    }

    @Test
    void testModuleRegistration() {
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(createTimestamp())
                .setVerifyRegistration(VerifyRegistrationCommand.newBuilder()
                        .setExpectedModuleName("test-processor")
                        .setCheckConsul(false) // Don't check Consul in unit tests
                        .build())
                .build();
        
        TestResult result = getTestHarness().executeTest(command)
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(result.getSuccess()).isTrue();
        
        TestEvent registrationEvent = findEventByType(result, TestEvent::hasModuleRegistered);
        assertThat(registrationEvent).isNotNull();
        assertThat(registrationEvent.getModuleRegistered().getSuccess()).isTrue();
        assertThat(registrationEvent.getModuleRegistered().getModuleName())
                .isEqualTo("test-processor");
    }

    @Test
    void testProcessDocumentWithRealData() {
        // Get a sample document
        PipeDoc sampleDoc = getTestDataHelper().getSamplePipeDocuments()
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sample documents available"));
        
        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(sampleDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("test-harness")
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .putConfigParams("mode", "test")
                        .build())
                .build();
        
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(createTimestamp())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(processRequest)
                        .setExpectSuccess(true)
                        .setTimeoutMs(5000)
                        .build())
                .build();
        
        TestResult result = getTestHarness().executeTest(command)
                .await().atMost(Duration.ofSeconds(10));
        
        assertThat(result.getSuccess()).isTrue();
        
        // Verify document received event
        TestEvent receivedEvent = findEventByType(result, TestEvent::hasDocumentReceived);
        assertThat(receivedEvent).isNotNull();
        assertThat(receivedEvent.getDocumentReceived().getDocumentId())
                .isEqualTo(sampleDoc.getId());
        
        // Verify document processed event
        TestEvent processedEvent = findEventByType(result, TestEvent::hasDocumentProcessed);
        assertThat(processedEvent).isNotNull();
        assertThat(processedEvent.getDocumentProcessed().getSuccess()).isTrue();
        assertThat(processedEvent.getDocumentProcessed().getDocumentId())
                .isEqualTo(sampleDoc.getId());
    }

    @Test
    void testWaitForEvent() {
        // First trigger some events
        TestCommand processCommand = TestCommand.newBuilder()
                .setCommandId("process-1")
                .setTimestamp(createTimestamp())
                .setCheckHealth(CheckHealthCommand.newBuilder().build())
                .build();
        
        getTestHarness().executeTest(processCommand)
                .await().atMost(Duration.ofSeconds(5));
        
        // Now wait for health check events
        TestCommand waitCommand = TestCommand.newBuilder()
                .setCommandId("wait-1")
                .setTimestamp(createTimestamp())
                .setWaitForEvent(WaitForEventCommand.newBuilder()
                        .addEventTypes("health_check")
                        .setTimeoutMs(2000)
                        .build())
                .build();
        
        TestResult result = getTestHarness().executeTest(waitCommand)
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessagesCount()).isGreaterThan(0);
    }

    @Test
    void testConfigureModule() {
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(createTimestamp())
                .setConfigureModule(ConfigureModuleCommand.newBuilder()
                        .setResetToDefaults(true)
                        .build())
                .build();
        
        TestResult result = getTestHarness().executeTest(command)
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessagesCount()).isGreaterThan(0);
        assertThat(result.getMessagesList())
                .anyMatch(msg -> msg.contains("Module configuration"));
    }

    @Test
    void testErrorScenario() {
        // Configure the module to simulate errors
        TestCommand configCommand = TestCommand.newBuilder()
                .setCommandId("config-error")
                .setTimestamp(createTimestamp())
                .setSimulateScenario(SimulateScenarioCommand.newBuilder()
                        .setScenario(SimulateScenarioCommand.Scenario.RANDOM_FAILURES)
                        .setDurationMs(10000)
                        .build())
                .build();
        
        TestResult configResult = getTestHarness().executeTest(configCommand)
                .await().atMost(Duration.ofSeconds(5));
        assertThat(configResult.getSuccess()).isTrue();
        
        // Try to process a document - it might fail
        PipeDoc doc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setTitle("Error Test")
                .setBody("This might fail")
                .build();
        
        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(doc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("error-test")
                        .setPipeStepName("test-harness")
                        .build())
                .build();
        
        TestCommand processCommand = TestCommand.newBuilder()
                .setCommandId("process-error")
                .setTimestamp(createTimestamp())
                .setProcessDocument(ProcessDocumentCommand.newBuilder()
                        .setRequest(processRequest)
                        .setExpectSuccess(false) // Don't require success
                        .setTimeoutMs(5000)
                        .build())
                .build();
        
        TestResult result = getTestHarness().executeTest(processCommand)
                .await().atMost(Duration.ofSeconds(10));
        
        // The command should complete, but the document processing might fail
        assertThat(result).isNotNull();
        
        // Look for error events
        TestEvent errorEvent = findEventByType(result, TestEvent::hasError);
        if (errorEvent != null) {
            assertThat(errorEvent.getError().getErrorType()).isNotEmpty();
            assertThat(errorEvent.getError().getErrorMessage()).isNotEmpty();
        }
        
        // Reset to normal
        TestCommand resetCommand = TestCommand.newBuilder()
                .setCommandId("reset")
                .setTimestamp(createTimestamp())
                .setSimulateScenario(SimulateScenarioCommand.newBuilder()
                        .setScenario(SimulateScenarioCommand.Scenario.NORMAL_PROCESSING)
                        .build())
                .build();
        
        getTestHarness().executeTest(resetCommand)
                .await().atMost(Duration.ofSeconds(5));
    }

    @Test
    void testGenericEvent() {
        // Test the generic event capability
        TestCommand command = TestCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setTimestamp(createTimestamp())
                .setCheckHealth(CheckHealthCommand.newBuilder()
                        .setIncludeDetails(true)
                        .build())
                .build();
        
        TestResult result = getTestHarness().executeTest(command)
                .await().atMost(Duration.ofSeconds(5));
        
        assertThat(result.getSuccess()).isTrue();
        
        // The harness might emit generic events for extensibility
        TestEvent genericEvent = findEventByType(result, TestEvent::hasGeneric);
        if (genericEvent != null) {
            assertThat(genericEvent.getGeneric().getEventType()).isNotEmpty();
            assertThat(genericEvent.getGeneric().hasData()).isTrue();
        }
    }

    // Helper methods

    protected TestEvent findEventByType(TestResult result, java.util.function.Predicate<TestEvent> predicate) {
        return result.getEventsList().stream()
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    protected com.google.protobuf.Timestamp createTimestamp() {
        long millis = System.currentTimeMillis();
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(millis / 1000)
                .setNanos((int) ((millis % 1000) * 1000000))
                .build();
    }
}