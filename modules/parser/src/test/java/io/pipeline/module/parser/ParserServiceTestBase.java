package io.pipeline.module.parser;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pipeline.data.model.Blob;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class ParserServiceTestBase {

    protected abstract PipeStepProcessor getParserService();

    @Test
    void testProcessData() {
        // Create a test document with blob data for parsing
        String testContent = "This is a sample text document that will be parsed by Tika.";
        Blob testBlob = Blob.newBuilder()
                .setData(com.google.protobuf.ByteString.copyFromUtf8(testContent))
                .setMimeType("text/plain")
                .setFilename("test.txt")
                .build();
        
        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBlob(testBlob)
                .build();

        // Create service metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("parser-step")
                .setStreamId(UUID.randomUUID().toString())
                .setCurrentHopNumber(1)
                .putContextParams("tenant", "test-tenant")
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                        .putFields("mode", Value.newBuilder().setStringValue("parser").build())
                        .build())
                .putConfigParams("mode", "parser")
                .build();

        // Create request
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();

        // Execute and verify
        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat("Parser should successfully process valid text document", response.getSuccess(), is(true));
        assertThat("Parser response should contain output document", response.hasOutputDoc(), is(true));
        assertThat("Output document ID should be preserved from input", response.getOutputDoc().getId(), is(equalTo(testDoc.getId())));
        assertThat("Parsed document body should contain original text content", response.getOutputDoc().getBody(), containsString("sample text document"));
        assertThat("Parser should generate processing logs", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Processing logs should indicate successful completion", response.getProcessorLogsList(), hasItem(containsString("successfully processed")));
    }

    @Test
    void testProcessDataWithoutDocument() {
        // Test with no document - should still succeed (parser service is tolerant)
        ProcessRequest request = ProcessRequest.newBuilder()
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("test-pipeline")
                        .setPipeStepName("parser-step")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder().build())
                // No document set
                .build();

        var response = getParserService().processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat("Parser should handle requests without documents gracefully", response.getSuccess(), is(true));
        assertThat("Response should not contain output document when no input provided", response.hasOutputDoc(), is(false));
        assertThat("Parser should generate logs even when no document provided", response.getProcessorLogsList(), is(not(empty())));
        assertThat("Processing logs should indicate no document was provided", response.getProcessorLogsList(), hasItem(containsString("no document")));
    }

    @Test
    void testGetServiceRegistration() {
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        var registration = getParserService().getServiceRegistration(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
        
        assertThat("Module name should be 'parser' in registration", registration.getModuleName(), is(equalTo("parser")));
        assertThat("Registration should include JSON configuration schema", registration.hasJsonConfigSchema(), is(true));
        assertThat("Health check should pass during registration", registration.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate no test was performed", registration.getHealthCheckMessage(), containsString("No health check performed"));
    }
}