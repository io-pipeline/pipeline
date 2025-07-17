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
 * Container integration test for gRPC health check.
 * Uses Quarkus-managed container with dynamic port allocation.
 * 
 * This verifies:
 * 1. Health check works in containerized production mode
 * 2. Standard gRPC clients can connect
 * 3. The unified HTTP/gRPC server responds correctly
 */
@QuarkusIntegrationTest
public class GrpcHealthCheckContainerIT extends GrpcHealthCheckTestBase {
    
    @TestHTTPResource
    URL testUrl;
    
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub healthService;
    
    @BeforeEach
    void setup() {
        // Extract host and port from the URL provided by Quarkus
        String host = testUrl.getHost();
        int port = testUrl.getPort();
        
        System.out.println("Connecting to containerized gRPC service at " + host + ":" + port);
        
        // Connect to the unified server port
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