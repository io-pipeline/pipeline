package com.rokkon.testmodule;

import com.rokkon.pipeline.testing.harness.grpc.TestHarness;
import com.rokkon.pipeline.testing.util.UnifiedTestProfile;
import com.rokkon.search.util.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Unit test for TestHarness using Quarkus Test framework.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class TestHarnessUnitTest extends TestHarnessTestBase {

    @GrpcClient
    TestHarness testHarness;

    @Inject
    ProtobufTestDataHelper testDataHelper;

    @Override
    protected TestHarness getTestHarness() {
        return testHarness;
    }
    
    @Override
    protected ProtobufTestDataHelper getTestDataHelper() {
        return testDataHelper;
    }
}