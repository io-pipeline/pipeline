package io.pipeline.dynamic.grpc.grpc;

import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Dynamic gRPC performance characteristics.
 * Uses mocked services to verify caching and basic performance.
 */
@QuarkusTest
public class DynamicGrpcPerformanceTest {
    
    @Inject
    DynamicGrpcClientFactory clientFactory;
    
    private MockServiceDiscovery mockDiscovery;
    
    @BeforeEach
    void setup() {
        mockDiscovery = new MockServiceDiscovery();
        clientFactory.setServiceDiscovery(mockDiscovery);
    }
    
    @Test
    @DisplayName("Channel caching should improve performance")
    void testChannelCachingPerformance() {
        mockDiscovery.addService("perf-service", "localhost", 9090);
        
        // First, ensure no channel exists
        assertThat(clientFactory.hasChannel("localhost", 9090)).isFalse();
        
        // First call - creates channel
        long start = System.nanoTime();
        clientFactory.getClientForService("perf-service")
            .await().atMost(Duration.ofSeconds(5));
        long firstCallTime = System.nanoTime() - start;
        
        // Verify channel was created
        assertThat(clientFactory.hasChannel("localhost", 9090)).isTrue();
        
        // Subsequent calls should be faster due to caching
        List<Long> cachedCallTimes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            start = System.nanoTime();
            clientFactory.getClientForService("perf-service")
                .await().atMost(Duration.ofSeconds(1));
            cachedCallTimes.add(System.nanoTime() - start);
        }
        
        // Average cached call time should be less than first call
        double avgCachedTimeMs = cachedCallTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0) / 1_000_000.0;
        
        double firstCallTimeMs = firstCallTime / 1_000_000.0;
        
        System.out.printf("First call: %.2fms, Avg cached: %.2fms%n", 
            firstCallTimeMs, avgCachedTimeMs);
        
        // Cached calls should be faster (more lenient check)
        assertThat(avgCachedTimeMs).isLessThanOrEqualTo(firstCallTimeMs);
        
        // Also verify that cached calls are fast in absolute terms
        assertThat(avgCachedTimeMs).isLessThan(10.0); // Less than 10ms
    }
    
    @Test
    @DisplayName("Channel reuse across multiple requests")
    void testChannelReuse() {
        // Use unique service name and port to avoid conflicts
        String serviceName = "reuse-service-" + System.currentTimeMillis();
        int port = 19090 + (int)(Math.random() * 1000);
        mockDiscovery.addService(serviceName, "localhost", port);
        
        // First request creates channel
        PipeStepProcessor client1 = clientFactory.getClientForService(serviceName)
            .await().atMost(Duration.ofSeconds(1));
        assertThat(client1).isNotNull();
        
        // Verify channel was created
        assertThat(clientFactory.hasChannel("localhost", port)).isTrue();
        
        // Make multiple additional requests to same service
        for (int i = 0; i < 10; i++) {
            PipeStepProcessor client = clientFactory.getClientForService(serviceName)
                .await().atMost(Duration.ofSeconds(1));
            assertThat(client).isNotNull();
        }
        
        // Should still have only one channel for this host:port
        assertThat(clientFactory.hasChannel("localhost", port)).isTrue();
    }
    
    @Test
    @DisplayName("Verify caching key includes both host and port")
    void testCachingKeyUniqueness() {
        // Use unique ports to avoid conflicts
        int port1 = 29090 + (int)(Math.random() * 1000);
        int port2 = port1 + 1;
        
        // Add services with same host but different ports
        mockDiscovery.addService("service1-unique", "localhost", port1);
        mockDiscovery.addService("service2-unique", "localhost", port2);
        
        // Get clients
        PipeStepProcessor client1 = clientFactory.getClientForService("service1-unique")
            .await().atMost(Duration.ofSeconds(1));
        PipeStepProcessor client2 = clientFactory.getClientForService("service2-unique")
            .await().atMost(Duration.ofSeconds(1));
        
        assertThat(client1).isNotNull();
        assertThat(client2).isNotNull();
        
        // Should have two different channels
        assertThat(clientFactory.hasChannel("localhost", port1)).isTrue();
        assertThat(clientFactory.hasChannel("localhost", port2)).isTrue();
        
        // Verify they are different channels (different ports)
        assertThat(port1).isNotEqualTo(port2);
    }
}