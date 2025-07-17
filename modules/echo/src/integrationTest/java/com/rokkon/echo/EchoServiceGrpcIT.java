package com.rokkon.echo;

import com.rokkon.search.model.*;
import com.rokkon.search.sdk.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for EchoService using real gRPC client.
 * This test runs against the packaged JAR as a black-box test.
 */
@QuarkusIntegrationTest
public class EchoServiceGrpcIT {

    private ManagedChannel channel;
    private PipeStepProcessor echoService;

    @BeforeEach
    void setUp() {
        // Get the test port from Quarkus configuration
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        
        System.out.println("Connecting gRPC client to localhost:" + port);

        // Create a real gRPC channel
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext() // No TLS for local tests
                .build();

        // Create the client using the generated PipeStepProcessorClient
        echoService = new PipeStepProcessorClient("echo", channel, (name, stub) -> stub);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testGetServiceRegistration() {
        // Prepare request
        RegistrationRequest request = RegistrationRequest.newBuilder().build();

        // Call the service and block for result
        ServiceRegistrationResponse response = echoService.getServiceRegistration(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.getModuleName()).isEqualTo("echo");
        assertThat(response.getHealthCheckPassed()).isTrue();
        assertThat(response.getHealthCheckMessage()).isEqualTo("Service is healthy");
        
        System.out.println("Received registration: " + response.getModuleName());
    }

    @Test
    void testProcessData() {
        // Build test document
        PipeDoc document = PipeDoc.newBuilder()
                .setId("test-doc-123")
                .setDocumentType("test")
                .setBody("Test content from integration test")
                .putMetadata("source", "integration-test")
                .build();

        // Build request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("integration-test")
                        .setPipeStepName("echo")
                        .setStreamId("test-stream-1")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        // Call the service and block for result
        ProcessResponse response = echoService.processData(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        
        PipeDoc returnedDoc = response.getOutputDoc();
        assertThat(returnedDoc.getId()).isEqualTo("test-doc-123");
        assertThat(returnedDoc.getBody()).isEqualTo("Test content from integration test");
        assertThat(returnedDoc.getMetadataMap()).containsKey("echo_processed");
        assertThat(returnedDoc.getMetadataMap().get("echo_processed")).isEqualTo("true");
        
        System.out.println("Document processed successfully: " + returnedDoc.getId());
    }

    @Test
    void testProcessDataWithEmptyDocument() {
        // Build empty document
        PipeDoc document = PipeDoc.newBuilder()
                .setId("empty-doc")
                .setDocumentType("test")
                .build();

        // Build request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(document)
                .setConfig(ProcessConfiguration.newBuilder().build())
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("integration-test")
                        .setPipeStepName("echo")
                        .setStreamId("test-stream-2")
                        .setCurrentHopNumber(1)
                        .build())
                .build();

        // Call the service and block for result
        ProcessResponse response = echoService.processData(request)
                .await().atMost(java.time.Duration.ofSeconds(5));

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
        
        PipeDoc returnedDoc = response.getOutputDoc();
        assertThat(returnedDoc.getId()).isEqualTo("empty-doc");
        assertThat(returnedDoc.getMetadataMap()).containsKey("echo_processed");
        
        System.out.println("Empty document processed successfully");
    }
}