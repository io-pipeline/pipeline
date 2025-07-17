package com.rokkon.echo;

import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import com.rokkon.search.sdk.ProcessRequest;
import com.rokkon.search.sdk.ServiceMetadata;
import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.util.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();

        // Verify document content is preserved
        PipeDoc outputDoc = response.getOutputDoc();
        assertThat(outputDoc.getId()).isEqualTo(sampleDoc.getId());
        assertThat(outputDoc.getBody()).isEqualTo(sampleDoc.getBody());
        if (sampleDoc.hasTitle()) {
            assertThat(outputDoc.getTitle()).isEqualTo(sampleDoc.getTitle());
        }

        // Verify echo metadata was added
        assertThat(outputDoc.hasCustomData()).isTrue();
        assertThat(outputDoc.getCustomData().getFieldsMap())
                .containsKey("processed_by_echo")
                .containsKey("echo_timestamp");
    }

    @Test
    void testProcessTikaDocuments() {
        // Test with Tika-parsed documents
        Collection<PipeDoc> tikaDocs = getTestDataHelper().getTikaPipeDocuments();
        assertThat(tikaDocs).isNotEmpty();

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

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();

        // Tika documents often have metadata - verify it's preserved
        if (tikaDoc.hasCustomData()) {
            assertThat(response.getOutputDoc().getCustomData().getFieldsMap())
                    .containsAllEntriesOf(tikaDoc.getCustomData().getFieldsMap());
        }
    }

    @Test
    void testProcessChunkerDocuments() {
        // Test with chunked documents
        Collection<PipeDoc> chunkerDocs = getTestDataHelper().getChunkerPipeDocuments();
        assertThat(chunkerDocs).isNotEmpty();

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

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();
    }

    @Test
    void testProcessPipeStream() {
        // Test with a complete PipeStream
        Collection<PipeStream> streams = getTestDataHelper().getPipeStreams();
        assertThat(streams).isNotEmpty();

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

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.hasOutputDoc()).isTrue();

        // Verify stream ID is captured in metadata
        assertThat(response.getOutputDoc().getCustomData().getFieldsMap())
                .containsEntry("echo_stream_id",
                        com.google.protobuf.Value.newBuilder()
                                .setStringValue(stream.getStreamId()).build());

    }

    @Test
    void testBulkProcessing() {
        // Test processing multiple documents in sequence
        Collection<PipeDoc> docs = getTestDataHelper().getSamplePipeDocuments();
        assertThat(docs).hasSizeGreaterThan(10);

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

            assertThat(response.getSuccess()).isTrue();
            processedCount++;
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("Processed %d documents in %d ms (%.2f docs/sec)%n", 
                processedCount, duration, (processedCount * 1000.0) / duration);

        assertThat(processedCount).isEqualTo(50);
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

            assertThat(response.getSuccess()).isTrue();
            assertThat(response.hasOutputDoc()).isTrue();
            assertThat(response.getOutputDoc().getBody()).isEqualTo(largeDoc.getBody());
        }
    }
}
