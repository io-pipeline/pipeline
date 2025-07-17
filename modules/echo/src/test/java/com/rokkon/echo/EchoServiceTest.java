package com.rokkon.echo;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.util.ProtobufTestDataHelper;
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