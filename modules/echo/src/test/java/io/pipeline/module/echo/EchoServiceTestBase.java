package io.pipeline.module.echo;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

public abstract class EchoServiceTestBase {

    protected abstract PipeStepProcessor getEchoService();

    /**
     * Get the ProtobufTestDataHelper instance.
     * Unit tests can inject it via CDI, while integration tests should create it directly.
     * @return ProtobufTestDataHelper instance
     */
    protected abstract ProtobufTestDataHelper getTestDataHelper();

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
                .setPipeStepName("echo-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("mode", Value.newBuilder().setStringValue("echo").build())
                        .build())
                .putConfigParams("mode", "echo")
                .build();

        // Create request
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));
        assertThat("Output document ID should match input document ID", response.getOutputDoc().getId(), equalTo(testDoc.getId()));
        assertThat("Output document body should match input document body", response.getOutputDoc().getBody(), equalTo(testDoc.getBody()));
        assertThat("Processor logs should not be empty", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Processor logs should contain success message", response.getProcessorLogsList(), hasItem(containsString("successfully processed")));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed (echo service is tolerant)
        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("echo-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful even without document", response.getSuccess(), is(true));
        assertThat("Response should not have output document", response.hasOutputDoc(), is(false));
        assertThat("Processor logs should not be empty", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Processor logs should contain success message", response.getProcessorLogsList(), hasItem(containsString("successfully processed")));
    }

    @Test
    void testGetServiceRegistrationWithoutHealthCheck() {
        // Call without test request
        RegistrationRequest request = RegistrationRequest.newBuilder().build();

        var registration = getEchoService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Module name should be 'echo'", registration.getModuleName(), equalTo("echo"));
        // Echo service has no JSON schema - it accepts any input
        assertThat("JSON config schema should not be present", registration.hasJsonConfigSchema(), is(false));
        // Should be healthy without test
        assertThat("Health check should pass", registration.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate service is healthy", registration.getHealthCheckMessage(), containsString("Service is healthy"));
    }

    @Test
    void testGetServiceRegistrationWithHealthCheck() {
        // Create a test document for health check
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("health-check-doc")
                .setBody("Health check test")
                .build();

        ModuleProcessRequest processRequest = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("health-check")
                        .setPipeStepName("echo-health")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                .build();

        // Call with test request for health check
        RegistrationRequest request = RegistrationRequest.newBuilder()
                .setTestRequest(processRequest)
                .build();

        var registration = getEchoService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Module name should be 'echo'", registration.getModuleName(), equalTo("echo"));
        assertThat("JSON config schema should not be present", registration.hasJsonConfigSchema(), is(false));
        // Health check should pass
        assertThat("Health check should pass with test request", registration.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate service is functioning correctly", 
                  registration.getHealthCheckMessage(), containsString("healthy and functioning correctly"));
    }

    @Test
    void testMetadataPropagation() {
        // Test that metadata is properly propagated
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("metadata-test")
                .setPipeStepName("echo-metadata")
                .setStreamId("stream-123")
                .setCurrentHopNumber(5)
                .putContextParams("tenant", "test-tenant")
                .putContextParams("region", "us-east-1")
                .build();

        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(PipeDoc.newBuilder()
                        .setId("metadata-test-doc")
                        .setBody("Test metadata propagation")
                        .build())
                .setMetadata(metadata)
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        // Verify metadata was added to custom_data
        var customData = response.getOutputDoc().getCustomData().getFieldsMap();
        assertThat("Custom data should contain stream ID", customData, hasKey("echo_stream_id"));
        assertThat("Stream ID should match input metadata", 
                  customData.get("echo_stream_id").getStringValue(), equalTo("stream-123"));
        assertThat("Custom data should contain step name", customData, hasKey("echo_step_name"));
        assertThat("Step name should match input metadata", 
                  customData.get("echo_step_name").getStringValue(), equalTo("echo-metadata"));
    }

    @Test
    void testLargeDocument() {
        // Test with a large document
        StringBuilder largeBody = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeBody.append("This is line ").append(i).append(" of a large document. ");
        }

        PipeDoc largeDoc = PipeDoc.newBuilder()
                .setId("large-doc")
                .setBody(largeBody.toString())
                .setTitle("Large Document Test")
                .build();

        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(largeDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("large-doc-test")
                        .setPipeStepName("echo-large")
                        .build())
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful with large document", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));
        assertThat("Output document body should match large input body", 
                  response.getOutputDoc().getBody(), equalTo(largeBody.toString()));
    }

    @Test
    void testExistingCustomData() {
        // Test that existing custom_data is preserved and extended
        Struct existingCustomData = Struct.newBuilder()
                .putFields("existing_field", Value.newBuilder().setStringValue("existing_value").build())
                .putFields("existing_number", Value.newBuilder().setNumberValue(42.0).build())
                .build();

        PipeDoc docWithCustomData = PipeDoc.newBuilder()
                .setId("custom-data-doc")
                .setBody("Document with existing custom data")
                .setCustomData(existingCustomData)
                .build();

        ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                .setDocument(docWithCustomData)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("custom-data-test")
                        .setPipeStepName("echo-custom")
                        .build())
                .build();

        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        var customData = response.getOutputDoc().getCustomData().getFieldsMap();
        // Original data should be preserved
        assertThat("Original field should be preserved", customData, hasKey("existing_field"));
        assertThat("Original field value should be preserved", 
                  customData.get("existing_field").getStringValue(), equalTo("existing_value"));
        assertThat("Original number field should be preserved", customData, hasKey("existing_number"));
        assertThat("Original number value should be preserved", 
                  customData.get("existing_number").getNumberValue(), equalTo(42.0));

        // New echo data should be added
        assertThat("Echo processor marker should be added", customData, hasKey("processed_by_echo"));
        assertThat("Echo timestamp should be added", customData, hasKey("echo_timestamp"));
    }

    @Test
    void testTestProcessData() {
        // Test the testProcessData method
        var response = getEchoService().testProcessData(null)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Test response should be successful", response.getSuccess(), is(true));
        assertThat("Test response should have output document", response.hasOutputDoc(), is(true));
        assertThat("Test document ID should have expected prefix", response.getOutputDoc().getId(), startsWith("test-doc-"));
        assertThat("Processor logs should contain test marker", response.getProcessorLogsList(), hasItem(containsString("[TEST]")));
        assertThat("Processor logs should contain validation success message", 
                  response.getProcessorLogsList(), hasItem(containsString("test validation completed successfully")));
    }
}
