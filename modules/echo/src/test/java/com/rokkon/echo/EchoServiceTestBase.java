package com.rokkon.echo;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import com.rokkon.search.util.ProtobufTestDataHelper;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getEchoService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).isEqualTo(testDoc.getId());
        assertThat(response.getOutputDoc().getBody()).isEqualTo(testDoc.getBody());
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("successfully processed"));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed (echo service is tolerant)
        ProcessRequest request = ProcessRequest.newBuilder()
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

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isFalse();
        assertThat(response.getProcessorLogsList()).isNotEmpty();
        assertThat(response.getProcessorLogsList()).anyMatch(log -> log.contains("successfully processed"));
    }

    @Test
    void testGetServiceRegistrationWithoutHealthCheck() {
        // Call without test request
        RegistrationRequest request = RegistrationRequest.newBuilder().build();

        var registration = getEchoService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(registration.getModuleName()).isEqualTo("echo");
        // Echo service has no JSON schema - it accepts any input
        assertThat(registration.hasJsonConfigSchema()).isFalse();
        // Should be healthy without test
        assertThat(registration.getHealthCheckPassed()).isTrue();
        assertThat(registration.getHealthCheckMessage()).contains("Service is healthy");
    }

    @Test
    void testGetServiceRegistrationWithHealthCheck() {
        // Create a test document for health check
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("health-check-doc")
                .setBody("Health check test")
                .build();

        ProcessRequest processRequest = ProcessRequest.newBuilder()
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

        assertThat(registration.getModuleName()).isEqualTo("echo");
        assertThat(registration.hasJsonConfigSchema()).isFalse();
        // Health check should pass
        assertThat(registration.getHealthCheckPassed()).isTrue();
        assertThat(registration.getHealthCheckMessage()).contains("healthy and functioning correctly");
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

        ProcessRequest request = ProcessRequest.newBuilder()
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

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();

        // Verify metadata was added to custom_data
        var customData = response.getOutputDoc().getCustomData().getFieldsMap();
        assertThat(customData).containsKey("echo_stream_id");
        assertThat(customData.get("echo_stream_id").getStringValue()).isEqualTo("stream-123");
        assertThat(customData).containsKey("echo_step_name");
        assertThat(customData.get("echo_step_name").getStringValue()).isEqualTo("echo-metadata");
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

        ProcessRequest request = ProcessRequest.newBuilder()
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

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getBody()).isEqualTo(largeBody.toString());
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

        ProcessRequest request = ProcessRequest.newBuilder()
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

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();

        var customData = response.getOutputDoc().getCustomData().getFieldsMap();
        // Original data should be preserved
        assertThat(customData).containsKey("existing_field");
        assertThat(customData.get("existing_field").getStringValue()).isEqualTo("existing_value");
        assertThat(customData).containsKey("existing_number");
        assertThat(customData.get("existing_number").getNumberValue()).isEqualTo(42.0);

        // New echo data should be added
        assertThat(customData).containsKey("processed_by_echo");
        assertThat(customData).containsKey("echo_timestamp");
    }

    @Test
    void testTestProcessData() {
        // Test the testProcessData method
        var response = getEchoService().testProcessData(null)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        assertThat(response.getOutputDoc().getId()).startsWith("test-doc-");
        assertThat(response.getProcessorLogsList())
                .anyMatch(log -> log.contains("[TEST]"))
                .anyMatch(log -> log.contains("test validation completed successfully"));
    }
}
