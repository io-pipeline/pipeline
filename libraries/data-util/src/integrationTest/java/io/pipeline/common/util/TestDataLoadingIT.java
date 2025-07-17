package io.pipeline.common.util;

import io.pipeline.data.util.TestDataLoadingTestBase;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration test for ProtobufTestDataHelper that loads data from packaged JAR.
 * Cannot use CDI injection in integration tests, so creates helper directly.
 */
@QuarkusIntegrationTest
class TestDataLoadingIT extends TestDataLoadingTestBase {

    // Create directly - integration tests run against packaged JAR
    private final ProtobufTestDataHelper testDataHelper = new ProtobufTestDataHelper();

    @Override
    protected ProtobufTestDataHelper getTestDataHelper() {
        return testDataHelper;
    }
}