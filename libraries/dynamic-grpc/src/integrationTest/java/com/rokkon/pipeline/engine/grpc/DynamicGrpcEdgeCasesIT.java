package com.rokkon.pipeline.engine.grpc;

import com.rokkon.pipeline.engine.client.DynamicGrpcClientFactory;
import com.rokkon.pipeline.engine.grpc.discovery.ServiceDiscovery;
import com.rokkon.search.sdk.PipeStepProcessor;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import org.junit.jupiter.api.*;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Dynamic gRPC edge cases with real Consul.
 * Focuses on scenarios that can be reliably tested in an integration environment.
 */
@Testcontainers
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicGrpcEdgeCasesIT {
    
    @Container
    static ConsulContainer consul = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
            .withCommand("agent -dev -client 0.0.0.0 -log-level error");
    
    private Vertx vertx;
    private ConsulClient consulClient;
    private ServiceDiscovery serviceDiscovery;
    private DynamicGrpcClientFactory clientFactory;
    
    @BeforeEach
    void setup() {
        vertx = Vertx.vertx();
        
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consul.getHost())
            .setPort(consul.getFirstMappedPort());
        
        consulClient = ConsulClient.create(vertx, options);
        serviceDiscovery = new DynamicGrpcIntegrationIT.TestConsulServiceDiscovery(consulClient);
        clientFactory = new DynamicGrpcClientFactory();
        clientFactory.setServiceDiscovery(serviceDiscovery);
    }
    
    @AfterEach
    void cleanup() {
        if (clientFactory != null) {
            clientFactory.shutdown();
        }
        if (consulClient != null) {
            consulClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Handle missing service gracefully")
    void testMissingService() {
        // Try to get a client for non-existent service
        Uni<PipeStepProcessor> clientUni = clientFactory.getClientForService("non-existent-service");
        
        assertThatThrownBy(() -> 
            clientUni.await().atMost(Duration.ofSeconds(5))
        ).hasMessageContaining("No healthy instances found");
    }
    
    @Test
    @Order(2)
    @DisplayName("Service discovery with multiple instances")
    void testMultipleServiceInstances() {
        String serviceName = "multi-instance-service";
        
        // Register multiple instances of the same service
        for (int i = 0; i < 3; i++) {
            ServiceOptions service = new ServiceOptions()
                .setName(serviceName)
                .setId(serviceName + "-" + i)
                .setAddress("localhost")
                .setPort(8080 + i) // Different ports
                .setTags(List.of("grpc", "test"));
            
            Uni.createFrom().completionStage(() -> 
                consulClient.registerService(service).toCompletionStage()
            ).await().atMost(Duration.ofSeconds(5));
        }
        
        // Give Consul time to register
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Discover all instances
        var instances = serviceDiscovery.discoverAllInstances(serviceName)
            .await().atMost(Duration.ofSeconds(5));
        
        assertThat(instances).hasSize(3);
        assertThat(instances).extracting(si -> si.getPort())
            .containsExactlyInAnyOrder(8080, 8081, 8082);
    }
    
    @Test
    @Order(3)
    @DisplayName("Channel cleanup after factory shutdown")
    void testChannelCleanupOnShutdown() {
        // Create a separate factory for this test
        DynamicGrpcClientFactory testFactory = new DynamicGrpcClientFactory();
        testFactory.setServiceDiscovery(serviceDiscovery);
        
        // Create some channels
        testFactory.getClient("localhost", 9090);
        testFactory.getClient("localhost", 9091);
        
        assertThat(testFactory.getActiveChannelCount()).isEqualTo(2);
        
        // Shutdown should clean up all channels
        testFactory.shutdown();
        assertThat(testFactory.getActiveChannelCount()).isEqualTo(0);
    }
}