package io.pipeline.module.echo;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.ProcessRequest;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

/**
 * Tests the Echo service with real test data from our test data collection.
 * This validates that the service can handle actual documents that flow through the pipeline.
 */
@QuarkusTest
class EchoServiceRealDataTest extends EchoServiceTestBase {

    @Inject
    @GrpcService
    EchoServiceImpl echoService;

    @Inject
    ProtobufTestDataHelper testDataHelper;

    @Override
    protected PipeStepProcessor getEchoService() {
        return echoService;
    }

    @Override
    protected ProtobufTestDataHelper getTestDataHelper() {
        return testDataHelper;
    }

    // Static helper for parameterized tests
    private static ProtobufTestDataHelper staticHelper;

    @org.junit.jupiter.api.BeforeAll
    static void initStaticHelper() {
        // Initialize static helper for parameterized tests
        staticHelper = new ProtobufTestDataHelper();
    }

    private static Stream<PipeDoc> sampleDocuments() {
        // Use the static helper for parameterized tests
        // Get first 10 sample documents for parameterized testing
        return staticHelper.getSamplePipeDocuments().stream().limit(10);
    }

    @ParameterizedTest
    @MethodSource("sampleDocuments")
    void testProcessSampleDocuments(PipeDoc sampleDoc) {
        // Create request with sample document
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(sampleDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("sample-data-test")
                        .setPipeStepName("echo-sample")
                        .setStreamId(UUID.randomUUID().toString())
                        .build())
                .build();

        var response = echoService.processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        // Verify document content is preserved
        PipeDoc outputDoc = response.getOutputDoc();
        assertThat("Output document ID should match input document ID", outputDoc.getId(), equalTo(sampleDoc.getId()));
        assertThat("Output document body should match input document body", outputDoc.getBody(), equalTo(sampleDoc.getBody()));
        if (sampleDoc.hasTitle()) {
            assertThat("Output document title should match input document title", outputDoc.getTitle(), equalTo(sampleDoc.getTitle()));
        }

        // Verify echo metadata was added
        assertThat("Output document should have custom data", outputDoc.hasCustomData(), is(true));
        assertThat("Custom data should contain echo processor marker", outputDoc.getCustomData().getFieldsMap(), hasKey("processed_by_echo"));
        assertThat("Custom data should contain timestamp", outputDoc.getCustomData().getFieldsMap(), hasKey("echo_timestamp"));
    }

    @Test
    void testProcessTikaDocuments() {
        // Test with Tika-parsed documents
        Collection<PipeDoc> tikaDocs = getTestDataHelper().getTikaPipeDocuments();
        assertThat("Tika document collection should not be empty", tikaDocs, is(not(empty())));

        // Process first Tika document
        PipeDoc tikaDoc = tikaDocs.iterator().next();
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(tikaDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("tika-test")
                        .setPipeStepName("echo-tika")
                        .build())
                .build();

        var response = echoService.processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful with Tika document", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        // Tika documents often have metadata - verify it's preserved
        if (tikaDoc.hasCustomData()) {
            // Check that all original entries are present in the output
            var outputFields = response.getOutputDoc().getCustomData().getFieldsMap();
            var inputFields = tikaDoc.getCustomData().getFieldsMap();
            
            for (String key : inputFields.keySet()) {
                assertThat("Output should contain original metadata key: " + key, 
                          outputFields, hasKey(key));
                assertThat("Output metadata value should match for key: " + key,
                          outputFields.get(key), equalTo(inputFields.get(key)));
            }
        }
    }

    @Test
    void testProcessChunkerDocuments() {
        // Test with chunked documents
        Collection<PipeDoc> chunkerDocs = getTestDataHelper().getChunkerPipeDocuments();
        assertThat("Chunker document collection should not be empty", chunkerDocs, is(not(empty())));

        // Process first chunked document
        PipeDoc chunkedDoc = chunkerDocs.iterator().next();
        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(chunkedDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("chunker-test")
                        .setPipeStepName("echo-chunker")
                        .putContextParams("chunk_index", "0")
                        .build())
                .build();

        var response = echoService.processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful with chunked document", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));
    }

    @Test
    void testProcessPipeStream() {
        // Test with a complete PipeStream
        Collection<PipeStream> streams = getTestDataHelper().getPipeStreams();
        assertThat("Stream collection should not be empty", streams, is(not(empty())));

        PipeStream stream = streams.iterator().next();

        // Process each document in the stream
        PipeDoc doc = stream.getDocument();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setDocument(doc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("stream-test")
                        .setPipeStepName("echo-stream")
                        .setStreamId(stream.getStreamId())
                        .build())
                .build();

        var response = echoService.processData(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat("Response should be successful with stream document", response.getSuccess(), is(true));
        assertThat("Response should have output document", response.hasOutputDoc(), is(true));

        // Verify stream ID is captured in metadata
        var expectedValue = com.google.protobuf.Value.newBuilder().setStringValue(stream.getStreamId()).build();
        var customData = response.getOutputDoc().getCustomData().getFieldsMap();
        
        assertThat("Custom data should contain stream ID", customData, hasKey("echo_stream_id"));
        assertThat("Stream ID in metadata should match input stream ID", 
                  customData.get("echo_stream_id"), equalTo(expectedValue));

    }

    @Test
    void testBulkProcessing() {
        // Test processing multiple documents in sequence
        Collection<PipeDoc> docs = getTestDataHelper().getSamplePipeDocuments();
        assertThat("Sample document collection should have more than 10 documents", 
                  docs.size(), greaterThan(10));

        int processedCount = 0;
        long startTime = System.currentTimeMillis();

        // Process first 50 documents
        for (PipeDoc doc : docs) {
            if (processedCount >= 50) break;

            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(doc)
                    .setMetadata(ServiceMetadata.newBuilder()
                            .setPipelineName("bulk-test")
                            .setPipeStepName("echo-bulk")
                            .setCurrentHopNumber(processedCount)
                            .build())
                    .build();

            var response = echoService.processData(request)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();

            assertThat("Each response in bulk processing should be successful", 
                      response.getSuccess(), is(true));
            processedCount++;
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("Processed %d documents in %d ms (%.2f docs/sec)%n", 
                processedCount, duration, (processedCount * 1000.0) / duration);

        assertThat("Should process exactly 50 documents", processedCount, equalTo(50));
    }

    @Test
    void testLargeDocumentFromTestData() {
        // Find a large document from our test data
        Collection<PipeDoc> docs = getTestDataHelper().getTikaPipeDocuments();

        PipeDoc largeDoc = docs.stream()
                .filter(doc -> doc.getBody().length() > 10000)
                .findFirst()
                .orElse(null);

        if (largeDoc != null) {
            System.out.printf("Testing with large document: %s (size: %d bytes)%n", 
                    largeDoc.getId(), largeDoc.getBody().length());

            ProcessRequest request = ProcessRequest.newBuilder()
                    .setDocument(largeDoc)
                    .setMetadata(ServiceMetadata.newBuilder()
                            .setPipelineName("large-doc-test")
                            .setPipeStepName("echo-large")
                            .build())
                    .build();

            var response = echoService.processData(request)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();

            assertThat("Response should be successful with large document", response.getSuccess(), is(true));
            assertThat("Response should have output document", response.hasOutputDoc(), is(true));
            assertThat("Output document body should match large input document body", 
                      response.getOutputDoc().getBody(), equalTo(largeDoc.getBody()));
        }
    }
}
