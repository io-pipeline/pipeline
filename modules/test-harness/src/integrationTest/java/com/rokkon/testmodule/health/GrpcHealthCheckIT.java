package com.rokkon.testmodule.health;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.URL;

/**
 * Integration test for gRPC health check using Quarkus-managed container.
 * This verifies health check works in production mode with dynamic port allocation.
 * Uses standard blocking stub to demonstrate protocol compatibility.
 */
@QuarkusIntegrationTest
public class GrpcHealthCheckIT extends GrpcHealthCheckTestBase {
    
    @TestHTTPResource
    URL testUrl;
    
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub healthService;
    
    @BeforeEach
    void setup() {
        // Extract host and port from the URL provided by Quarkus
        String host = testUrl.getHost();
        int port = testUrl.getPort();
        
        System.out.println("Connecting to gRPC health service at " + host + ":" + port);
        
        channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        healthService = HealthGrpc.newBlockingStub(channel);
    }
    
    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.shutdown();
        }
    }
    
    @Override
    protected HealthGrpc.HealthBlockingStub getHealthService() {
        return healthService;
    }
}