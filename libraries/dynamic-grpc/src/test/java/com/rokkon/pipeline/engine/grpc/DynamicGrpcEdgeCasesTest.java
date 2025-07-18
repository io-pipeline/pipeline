package com.rokkon.pipeline.engine.grpc;

import com.rokkon.pipeline.engine.client.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Dynamic gRPC edge cases.
 * Uses mocked service discovery for fast, isolated testing.
 */
@QuarkusTest
public class DynamicGrpcEdgeCasesTest {
    
    @Inject
    DynamicGrpcClientFactory clientFactory;
    
    private MockServiceDiscovery mockDiscovery;
    
    @BeforeEach
    void setup() {
        mockDiscovery = new MockServiceDiscovery();
        clientFactory.setServiceDiscovery(mockDiscovery);
    }
    
    @Test
    @DisplayName("Service discovery timeout should propagate properly")
    void testServiceDiscoveryTimeout() {
        // Configure mock to delay response
        mockDiscovery.setDelayMillis(3000);
        mockDiscovery.addService("timeout-service", "localhost", 9090);
        
        // Try to discover with short timeout
        assertThatThrownBy(() -> 
            clientFactory.getClientForService("timeout-service")
                .await().atMost(Duration.ofMillis(500))
        ).isInstanceOf(io.smallrye.mutiny.TimeoutException.class);
    }
    
    @Test
    @DisplayName("Empty service list should throw proper exception")
    void testEmptyServiceList() {
        // Don't add any services
        
        Uni<com.rokkon.search.sdk.PipeStepProcessor> clientUni = 
            clientFactory.getClientForService("non-existent-service");
        
        assertThatThrownBy(() -> 
            clientUni.await().atMost(Duration.ofSeconds(2))
        ).hasMessageContaining("Service not found: non-existent-service");
    }
    
    @Test
    @DisplayName("Handle invalid service names")
    void testInvalidServiceNames() {
        // Test null service name
        assertThatThrownBy(() ->
            clientFactory.getClientForService(null)
                .await().atMost(Duration.ofSeconds(1))
        ).isInstanceOf(NullPointerException.class);
        
        // Test empty service name
        assertThatThrownBy(() ->
            clientFactory.getClientForService("")
                .await().atMost(Duration.ofSeconds(1))
        ).hasMessageContaining("Service not found");
        
        // Test service name with special characters
        assertThatThrownBy(() ->
            clientFactory.getClientForService("service@#$%")
                .await().atMost(Duration.ofSeconds(1))
        ).hasMessageContaining("Service not found");
    }
    
    @Test
    @DisplayName("Concurrent service discovery should not create duplicate channels")
    void testConcurrentServiceDiscovery() throws InterruptedException {
        mockDiscovery.addService("concurrent-service", "localhost", 9090);
        int initialChannelCount = clientFactory.getActiveChannelCount();
        
        // Launch multiple concurrent requests
        int concurrentRequests = 10;
        AtomicInteger completedRequests = new AtomicInteger(0);
        
        for (int i = 0; i < concurrentRequests; i++) {
            Uni.createFrom().item(i)
                .onItem().transformToUni(idx -> 
                    clientFactory.getClientForService("concurrent-service")
                )
                .subscribe().with(
                    client -> completedRequests.incrementAndGet(),
                    error -> completedRequests.incrementAndGet()
                );
        }
        
        // Wait for all requests to complete
        Thread.sleep(1000);
        
        // Should only create one channel despite concurrent requests
        assertThat(clientFactory.getActiveChannelCount())
            .isEqualTo(initialChannelCount + 1);
    }
    
    @Test
    @DisplayName("Channel cleanup after shutdown")
    void testChannelCleanup() {
        mockDiscovery.addService("cleanup-service", "localhost", 9091);
        
        // Create a client
        clientFactory.getClient("localhost", 9091);
        assertThat(clientFactory.hasChannel("localhost", 9091)).isTrue();
        
        // Shutdown should clean up channels
        clientFactory.shutdown();
        assertThat(clientFactory.getActiveChannelCount()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("Multiple channels for different hosts")
    void testMultipleChannels() {
        // Create channels for different hosts
        clientFactory.getClient("host1", 9090);
        clientFactory.getClient("host2", 9091);
        clientFactory.getClient("host3", 9092);
        
        // Should have 3 active channels
        assertThat(clientFactory.getActiveChannelCount()).isEqualTo(3);
        
        // Each should be tracked separately
        assertThat(clientFactory.hasChannel("host1", 9090)).isTrue();
        assertThat(clientFactory.hasChannel("host2", 9091)).isTrue();
        assertThat(clientFactory.hasChannel("host3", 9092)).isTrue();
        assertThat(clientFactory.hasChannel("host4", 9093)).isFalse();
    }
}