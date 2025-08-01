package io.pipeline.module.testharness.health;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test to prove that standard gRPC clients can connect to Mutiny services.
 * This demonstrates that the gRPC protocol is the same regardless of stub type.
 */
@QuarkusTest
class MixedHealthCheckTest {
    
    @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
    int httpPort;
    
    // Removed Mutiny client since we're testing with standard stubs
    
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub blockingHealthService;
    private HealthGrpc.HealthStub asyncHealthService;
    
    @BeforeEach
    void setup() {
        // Create a channel to the unified server (HTTP port)
        channel = ManagedChannelBuilder
                .forAddress("localhost", httpPort)
                .usePlaintext()
                .build();
        
        // Create both blocking and async stubs
        blockingHealthService = HealthGrpc.newBlockingStub(channel);
        asyncHealthService = HealthGrpc.newStub(channel);
    }
    
    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }
    
    
    @Test
    void testBlockingClientCanCallMutinyService() {
        // Use the already configured channel
        HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
        
        // Standard blocking client calling Mutiny service
        HealthCheckResponse response = blockingHealthService.check(request);
        
        // Convert AssertJ to Hamcrest
        assertThat("Health status should be SERVING", response.getStatus(), is(HealthCheckResponse.ServingStatus.SERVING));
    }
    
    @Test
    void testAsyncClientCanCallMutinyService() throws InterruptedException {
        // Use the already configured channel
        HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
        
        // Use a simple callback to capture the response
        var responseHolder = new Object() {
            volatile HealthCheckResponse response = null;
            volatile Throwable error = null;
        };
        
        asyncHealthService.check(request, new io.grpc.stub.StreamObserver<HealthCheckResponse>() {
            @Override
            public void onNext(HealthCheckResponse value) {
                responseHolder.response = value;
            }
            
            @Override
            public void onError(Throwable t) {
                responseHolder.error = t;
            }
            
            @Override
            public void onCompleted() {
                // Nothing to do
            }
        });
        
        // Wait a bit for the async response
        Thread.sleep(100);
        
        // Convert AssertJ to Hamcrest
        assertThat("Async call should not have error", responseHolder.error, is(nullValue()));
        assertThat("Async response should not be null", responseHolder.response, is(notNullValue()));
        assertThat("Async health status should be SERVING", responseHolder.response.getStatus(), is(HealthCheckResponse.ServingStatus.SERVING));
    }
}