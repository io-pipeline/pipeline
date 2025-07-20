package io.pipeline.module.draft;

import io.pipeline.data.module.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Test to verify that the @ProcessingBuffered interceptor works correctly.
 * This test specifically validates that the interceptor captures output documents
 * when processing.buffer.enabled=true.
 */
@QuarkusTest
@TestProfile(ProcessingBufferTestProfile.class)
public class ProcessingBufferInterceptorTest extends ProcessingBufferInterceptorTestBase {

    @GrpcClient
    PipeStepProcessor draftService;

    @Override
    protected PipeStepProcessor getDraftService() {
        return draftService;
    }
}