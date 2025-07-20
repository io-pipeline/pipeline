package io.pipeline.module.echo;

import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

@QuarkusTest
class TestDataDebugTest {
    
    private static final Logger LOG = Logger.getLogger(TestDataDebugTest.class);

    @Inject
    ProtobufTestDataHelper helper;

    @Test
    void debugTestDataLoading() {
        LOG.info("=== Debugging Test Data Loading ===");

        // Try to load with ProtobufTestDataHelper
        LOG.info("Trying to load data with ProtobufTestDataHelper:");
        LOG.infof("Tika documents: %d", helper.getTikaPipeDocuments().size());
        LOG.infof("Sample documents: %d", helper.getSamplePipeDocuments().size());
        LOG.infof("Chunker documents: %d", helper.getChunkerPipeDocuments().size());
    }
}
