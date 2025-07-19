package io.pipeline.module.echo;

import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class EchoServiceTest extends EchoServiceTestBase {

    @Inject
    @GrpcService
    EchoServiceImpl echoService;
    
    @Inject
    ProtobufTestDataHelper testDataHelper;

    @Override
    protected PipeStepProcessor getEchoService() {
        return echoService;
    }
    
    @Override
    protected ProtobufTestDataHelper getTestDataHelper() {
        return testDataHelper;
    }
}