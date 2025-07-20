package io.pipeline.module.draft;

import io.pipeline.data.module.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Test that validates @ProcessingBuffered interceptor behavior when DISABLED.
 * This test ensures that when enabled=false, the interceptor has no impact on processing.
 */
@QuarkusTest
@TestProfile(ProcessingBufferDisabledTestProfile.class)
public class ProcessingBufferDisabledTest extends ProcessingBufferEnabledDisabledTestBase {

    @GrpcClient
    PipeStepProcessor draftService;

    @Override
    protected PipeStepProcessor getDraftService() {
        return draftService;
    }
}