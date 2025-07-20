package io.pipeline.module.draft;

import io.pipeline.data.module.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Test that validates @ProcessingBuffered interceptor behavior when ENABLED.
 * This test ensures that buffer capture works correctly without affecting normal processing.
 */
@QuarkusTest
@TestProfile(ProcessingBufferEnabledTestProfile.class)
public class ProcessingBufferEnabledTest extends ProcessingBufferEnabledDisabledTestBase {

    @GrpcClient
    PipeStepProcessor draftService;

    @Override
    protected PipeStepProcessor getDraftService() {
        return draftService;
    }
}