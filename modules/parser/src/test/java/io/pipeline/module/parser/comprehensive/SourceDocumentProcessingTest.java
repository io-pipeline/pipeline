package io.pipeline.module.parser.comprehensive;

// import com.rokkon.test.util.DocumentProcessingSummary; // TODO: Fix static method issue with Quarkus
import io.pipeline.common.util.ProcessingBuffer;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.module.*;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.BeforeAll;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test for processing source documents through the parser.
 * This test loads the 126 source documents, processes them through the parser service,
 * and captures both input and output in separate buffers.
 * <p>
 * To run this test and generate test data:
 * ./gradlew test -Dtest=SourceDocumentProcessingTest
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SourceDocumentProcessingTest {
    private static final Logger LOG = Logger.getLogger(SourceDocumentProcessingTest.class);

    @Inject
    @GrpcClient
    PipeStepProcessor parserService;

    @Inject
    @Named("parserOutputBuffer")
    ProcessingBuffer<PipeDoc> outputBuffer;

    @Inject
    @Named("parserInputBuffer")
    ProcessingBuffer<PipeStream> inputBuffer;

    private ProtobufTestDataHelper testDataHelper;

    @BeforeAll
    public void setup() {
        LOG.infof("Output buffer enabled: %b", outputBuffer.size() >= 0);
        LOG.infof("Input buffer enabled: %b", inputBuffer.size() >= 0);
        
        // Create test data helper manually
        testDataHelper = new ProtobufTestDataHelper();
    }

    @Test
    public void processSourceDocumentsAndGenerateTestData() throws Exception {
        // Get the Tika request streams (these contain the original source documents as blobs)
        var tikaRequestStreams = testDataHelper.getTikaRequestStreams();
        LOG.infof("Loaded %d Tika request streams with source documents", tikaRequestStreams.size());

        // Create output directories
        Path inputDir = Paths.get("build/test-data/parser/input");
        Path outputDir = Paths.get("build/test-data/parser/output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Process each stream
        int successCount = 0;
        int failureCount = 0;
        List<String> failedDocuments = new ArrayList<>();

        for (PipeStream requestStream : tikaRequestStreams) {
            try {
                // Add the input stream to the input buffer
                inputBuffer.add(requestStream);
                
                if (requestStream.hasDocument()) {
                    PipeDoc requestDoc = requestStream.getDocument();
                    
                    // Create request
                    ModuleProcessRequest request = createProcessRequest(requestDoc);

                    // Process document
                    UniAssertSubscriber<ModuleProcessResponse> subscriber = parserService.processData(request)
                            .subscribe().withSubscriber(UniAssertSubscriber.create());
                    
                    ModuleProcessResponse response = subscriber
                            .awaitItem()
                            .assertCompleted()
                            .getItem();

                    // Check if processing was successful
                    if (response.getSuccess() && response.hasOutputDoc()) {
                        successCount++;
                        LOG.debugf("Successfully processed document: %s", requestDoc.getId());
                    } else {
                        failureCount++;
                        String docInfo = String.format("%s (%s)", requestDoc.getId(), 
                                requestDoc.hasBlob() ? requestDoc.getBlob().getFilename() : "no filename");
                        failedDocuments.add(docInfo);
                        LOG.warnf("Failed to process document: %s", docInfo);
                        for (String log : response.getProcessorLogsList()) {
                            LOG.warnf("  %s", log);
                        }
                    }
                } else {
                    LOG.warnf("Stream %s has no document", requestStream.getStreamId());
                }
            } catch (Exception e) {
                failureCount++;
                if (requestStream.hasDocument()) {
                    PipeDoc doc = requestStream.getDocument();
                    String docInfo = String.format("%s (%s)", doc.getId(), 
                            doc.hasBlob() ? doc.getBlob().getFilename() : "no filename");
                    failedDocuments.add(docInfo);
                    LOG.errorf(e, "Error processing document: %s", docInfo);
                } else {
                    LOG.errorf(e, "Error processing stream: %s", requestStream.getStreamId());
                }
            }
        }

        // Log results
        LOG.infof("Processed %d streams: %d successful, %d failed", 
                tikaRequestStreams.size(), successCount, failureCount);
        LOG.infof("Captured %d input streams in buffer", inputBuffer.size());
        LOG.infof("Captured %d output documents in buffer", outputBuffer.size());
        
        // Generate metadata summary
        // TODO: Re-enable when DocumentProcessingSummary is made non-static for Quarkus
        // DocumentProcessingSummary.generateSummary(outputBuffer, failureCount, failedDocuments);
        LOG.info("\n=== Document Processing Summary ===");
        LOG.infof("Total documents processed: %d", outputBuffer.size() + failureCount);
        LOG.infof("Successfully parsed: %d", outputBuffer.size());
        LOG.infof("Failed to parse: %d", failureCount);
        if (failureCount > 0) {
            LOG.info("\n--- Failed Documents ---");
            for (String failedDoc : failedDocuments) {
                LOG.infof("  %s", failedDoc);
            }
        }

        // Save buffers to disk
        if (inputBuffer.size() > 0) {
            LOG.infof("Saving input buffer to %s", inputDir);
            inputBuffer.saveToDisk(inputDir, "parser_input", 3);
            LOG.infof("Saved %d input streams to %s", inputBuffer.size(), inputDir);
        }

        if (outputBuffer.size() > 0) {
            LOG.infof("Saving output buffer to %s", outputDir);
            outputBuffer.saveToDisk(outputDir, "parser_output", 3);
            LOG.infof("Saved %d output documents to %s", outputBuffer.size(), outputDir);
        }

        // Log instructions for copying the files
        LOG.info("\n=== Test Data Generation Complete ===");
        LOG.info("To update the test data:");
        LOG.infof("1. Copy input files from %s to test-utilities/src/main/resources/test-data/parser/input/", 
                inputDir.toAbsolutePath());
        LOG.infof("2. Copy output files from %s to test-utilities/src/main/resources/test-data/parser/output/", 
                outputDir.toAbsolutePath());
    }

    private ModuleProcessRequest createProcessRequest(PipeDoc doc) {
        // Create metadata
        ServiceMetadata metadata = ServiceMetadata.newBuilder()
                .setPipelineName("test-data-generation")
                .setPipeStepName("parser-step")
                .setStreamId(UUID.randomUUID().toString())
                .build();

        // Create configuration
        ProcessConfiguration config = ProcessConfiguration.newBuilder()
                .putConfigParams("extractMetadata", "true")
                .putConfigParams("maxContentLength", "10000000")  // 10MB limit
                .build();

        // Create request
        return ModuleProcessRequest.newBuilder()
                .setDocument(doc)
                .setMetadata(metadata)
                .setConfig(config)
                .build();
    }
}