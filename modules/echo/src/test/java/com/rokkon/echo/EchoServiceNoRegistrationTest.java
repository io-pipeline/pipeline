package com.rokkon.echo;

import com.rokkon.search.sdk.PipeStepProcessor;
import com.rokkon.search.util.ProtobufTestDataHelper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * This test verifies that the Echo service can start and function correctly
 * without registration enabled. It uses the application-no-registration.properties
 * configuration which explicitly disables registration.
 */
@QuarkusTest
@TestProfile(NoRegistrationTestProfile.class)
class EchoServiceNoRegistrationTest extends EchoServiceTestBase {

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
    
    /**
     * Additional test to verify that the service can start without registration.
     * The fact that this test runs at all confirms that the service can start
     * without registration, as the application-no-registration.properties
     * configuration explicitly disables registration.
     */
    @Test
    void testServiceStartsWithoutRegistration() {
        // If this test runs, it means the service started successfully without registration
        // All the inherited tests from EchoServiceTestBase will also run to verify functionality
    }
}