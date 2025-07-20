package io.pipeline.module.echo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import org.jboss.logging.Logger;

/**
 * Integration test for EchoService using real gRPC client.
 * This test runs against the packaged JAR as a black-box test.
 */
@QuarkusIntegrationTest
public class EchoServiceGrpcIT {
    
    private static final Logger LOG = Logger.getLogger(EchoServiceGrpcIT.class);

    private ManagedChannel channel;
    private PipeStepProcessor echoService;

    @BeforeEach
    void setUp() {
        // Get the test port from Quarkus configuration
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        
        LOG.infof("Connecting gRPC client to localhost:%d", port);

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
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Module name should be 'echo'", response.getModuleName(), equalTo("echo"));
        assertThat("Health check should pass", response.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate service is healthy", 
                  response.getHealthCheckMessage(), equalTo("Service is healthy"));
        
        LOG.infof("Received registration: %s", response.getModuleName());
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
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));
        
        PipeDoc returnedDoc = response.getOutputDoc();
        assertThat("Returned document ID should match input ID", returnedDoc.getId(), equalTo("test-doc-123"));
        assertThat("Returned document body should match input body", 
                  returnedDoc.getBody(), equalTo("Test content from integration test"));
        assertThat("Metadata should contain echo_processed flag", returnedDoc.getMetadataMap(), hasKey("echo_processed"));
        assertThat("Echo processed flag should be true", 
                  returnedDoc.getMetadataMap().get("echo_processed"), equalTo("true"));
        
        LOG.infof("Document processed successfully: %s", returnedDoc.getId());
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
        assertThat("Response should not be null", response, notNullValue());
        assertThat("Response should be successful even with empty document", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));
        
        PipeDoc returnedDoc = response.getOutputDoc();
        assertThat("Returned document ID should match input ID", returnedDoc.getId(), equalTo("empty-doc"));
        assertThat("Metadata should contain echo_processed flag", returnedDoc.getMetadataMap(), hasKey("echo_processed"));
        
        LOG.info("Empty document processed successfully");
    }
}