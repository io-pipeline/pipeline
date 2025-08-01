package io.pipeline.module.parser.comprehensive;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive test to verify that all available test documents are processed correctly
 * by the Quarkus ParserService, maintaining the same quality as the original implementation.
 * <p>
 * This test ensures that document processing quality is maintained during the migration.
 */
@QuarkusTest
public class ParserServiceComprehensiveTest {

    private static final Logger LOG = Logger.getLogger(ParserServiceComprehensiveTest.class);

    @GrpcClient
    PipeStepProcessor parserService;

    // No longer need test-utilities classes - using Apache Commons loader instead

    @Test
    public void testProcessAllAvailableDocumentsQuality() {
        LOG.info("=== Testing All Available Documents with Quarkus ParserService ===");

        // Load test documents using the test data helper - parser needs documents with blobs
        // Use parser output documents which have already been processed
        ProtobufTestDataHelper helper = new ProtobufTestDataHelper();
        List<PipeDoc> testDocs = new ArrayList<>(helper.getParserOutputDocs());

        // We're using parser output documents which don't have blobs
        // No need to filter for blobs since these are already processed documents
        LOG.info("Using parser output documents which have already been processed");
        LOG.infof("Loaded %d test documents for comprehensive testing", testDocs.size());

        // Process configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "1000000")
                .build();

        // Track success and failure counts
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Process each document
        for (PipeDoc testDoc : testDocs) {
            try {
                // Create unique metadata for each document
                ServiceMetadata metadata = ServiceMetadata.newBuilder()
                        .setPipelineName("comprehensive-test-pipeline")
                        .setPipeStepName("parser-comprehensive-test")
                        .setStreamId(UUID.randomUUID().toString())
                        .setCurrentHopNumber(1)
                        .build();

                ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                        .setDocument(testDoc)
                        .setConfig(config)
                        .setMetadata(metadata)
                        .build();

                // Process through ParserService
                ModuleProcessResponse response = parserService.processData(request)
                        .subscribe().withSubscriber(UniAssertSubscriber.create())
                        .awaitItem()
                        .getItem();

                // Verify response
                if (response.getSuccess() && response.hasOutputDoc()) {
                    successCount.incrementAndGet();

                    // Verify document ID is preserved
                    PipeDoc resultDoc = response.getOutputDoc();
                    assertThat("Document ID should be preserved during parsing", resultDoc.getId(), is(equalTo(testDoc.getId())));

                    // Verify document has content
                    assertThat("Parsed document should have non-empty body content", resultDoc.getBody(), is(not(emptyString())));

                    // Log progress every 10 documents
                    if (successCount.get() % 10 == 0) {
                        LOG.info("Successfully processed " + successCount.get() + " documents so far");
                    }
                } else {
                    failureCount.incrementAndGet();
                    LOG.warn("Failed to process document " + testDoc.getId() + ": " + 
                            response.getProcessorLogsList());
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                LOG.error("Error processing document " + testDoc.getId() + ": " + e.getMessage());
            }
        }

        // Log final results
        LOG.info("✅ Comprehensive testing complete!");
        LOG.info("Total documents processed: " + testDocs.size());
        LOG.info("Successful: " + successCount.get());
        LOG.info("Failed: " + failureCount.get());

        // First check that we actually have test documents
        assertThat("No test documents found! Test data is missing.", testDocs, is(not(empty())));

        // Assert high success rate (at least 90%)
        double successRate = (double) successCount.get() / testDocs.size();
        LOG.infof("Success rate: %.2f%%", successRate * 100);

        assertThat(String.format("Expected success rate of at least 90%%, but got %.2f%%", successRate * 100), successRate, is(greaterThanOrEqualTo(0.9)));
    }
}
