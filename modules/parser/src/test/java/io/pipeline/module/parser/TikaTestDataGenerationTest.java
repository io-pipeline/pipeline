package io.pipeline.module.parser;

import io.pipeline.common.util.ProcessingBuffer;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test for generating Tika test data.
 * This test loads all Tika request documents, processes them through the parser service,
 * and captures the output in the buffer.
 * 
 * To run this test and generate test data, use:
 * mvn test -Dtest=TikaTestDataGenerationTest -Dprocessing.buffer.enabled=true -Dprocessing.buffer.directory=target/test-data/tika/responses
 */
@QuarkusTest
public class TikaTestDataGenerationTest {
    private static final Logger LOG = Logger.getLogger(TikaTestDataGenerationTest.class);

    @Inject
    @GrpcClient
    PipeStepProcessor parserService;

    @Inject
    ProcessingBuffer<PipeDoc> outputBuffer;

    private ProtobufTestDataHelper testDataHelper;

    @Test
    public void generateTikaTestData() throws Exception {
        // Initialize test data helper
        testDataHelper = new ProtobufTestDataHelper();
        
        // Check if buffer is enabled
        if (outputBuffer.size() == 0 && !isBufferEnabled()) {
            LOG.warn("Processing buffer is disabled. Enable it with -Dprocessing.buffer.enabled=true");
            LOG.warn("Test will run but no data will be captured.");
        }

        // Load all Tika request documents
        List<PipeDoc> requestDocs = new ArrayList<>();
        testDataHelper.getTikaRequestStreams().forEach(stream -> {
            if (stream.hasDocument()) {
                requestDocs.add(stream.getDocument());
            }
        });
        LOG.infof("Loaded %d Tika request documents", requestDocs.size());

        // Create output directory if it doesn't exist
        Path outputDir = Paths.get("target/test-data/tika/responses");
        Files.createDirectories(outputDir);

        // Process each document
        int successCount = 0;
        int failureCount = 0;

        for (PipeDoc requestDoc : requestDocs) {
            try {
                // Create request
                ModuleProcessRequest request = createProcessRequest(requestDoc);

                // Process document
                ModuleProcessResponse response = parserService.processData(request)
                        .subscribe().withSubscriber(UniAssertSubscriber.create())
                        .awaitItem()
                        .getItem();

                // Check if processing was successful
                if (response.getSuccess() && response.hasOutputDoc()) {
                    successCount++;
                    LOG.infof("Successfully processed document: %s", requestDoc.getId());
                } else {
                    failureCount++;
                    LOG.warnf("Failed to process document: %s", requestDoc.getId());
                    for (String log : response.getProcessorLogsList()) {
                        LOG.warnf("  %s", log);
                    }
                }
            } catch (Exception e) {
                failureCount++;
                LOG.errorf(e, "Error processing document: %s", requestDoc.getId());
            }
        }

        // Log results
        LOG.infof("Processed %d documents: %d successful, %d failed", 
                requestDocs.size(), successCount, failureCount);
        LOG.infof("Captured %d documents in buffer", outputBuffer.size());

        // Save buffer to disk
        if (outputBuffer.size() > 0) {
            LOG.infof("Saving buffer to %s", outputDir);
            outputBuffer.saveToDisk(outputDir, "tika_response", 3);
            LOG.infof("Saved %d documents to %s", outputBuffer.size(), outputDir);

            // Log instructions for copying the files
            LOG.infof("To update the test data, copy the files from %s to test-utilities/src/main/resources/test-data/tika/responses/", 
                    outputDir.toAbsolutePath());
        }
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

    private boolean isBufferEnabled() {
        return Boolean.getBoolean("processing.buffer.enabled");
    }
}
