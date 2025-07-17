package com.rokkon.testmodule;

import com.rokkon.pipeline.testing.util.UnifiedTestProfile;
import com.rokkon.search.sdk.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Unit test for TestProcessor using Quarkus Test framework.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class TestProcessorUnitTest extends TestProcessorTestBase {

    @GrpcClient
    PipeStepProcessor pipeStepProcessor;

    @Override
    protected PipeStepProcessor getTestProcessor() {
        return pipeStepProcessor;
    }
}