package io.pipeline.module.chunker;

import io.pipeline.data.module.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;


@QuarkusTest
class ChunkerServiceTest extends ChunkerServiceTestBase {

    @GrpcClient("chunker")
    PipeStepProcessor chunkerService;

    @Override
    protected PipeStepProcessor getChunkerService() {
        return chunkerService;
    }
}