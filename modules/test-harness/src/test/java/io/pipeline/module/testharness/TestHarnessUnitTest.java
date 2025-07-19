package io.pipeline.module.testharness;


import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.pipeline.testing.harness.grpc.TestHarness;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for TestHarness using Quarkus Test framework.
 */
@QuarkusTest
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