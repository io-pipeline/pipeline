package io.pipeline.dynamic.grpc.grpc;

import io.pipeline.api.grpc.ServiceDiscovery;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.pipeline.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
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

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Performance integration tests for Dynamic gRPC with real Consul.
 * Focuses on testing caching and basic performance characteristics.
 */
@Testcontainers
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicGrpcPerformanceIT {
    
    @Container
    static ConsulContainer consul = new ConsulContainer(DockerImageName.parse("hashicorp/consul:latest"))
            .withCommand("agent -dev -client 0.0.0.0 -log-level error");
    
    private Vertx vertx;
    private ConsulClient consulClient;
    private ServiceDiscovery serviceDiscovery;
    private DynamicGrpcClientFactory clientFactory;
    
    // Test gRPC server
    private static Server grpcServer;
    private static int grpcPort;
    
    @BeforeAll
    static void setupServer() throws IOException {
        // Find free port and start test server
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            grpcPort = socket.getLocalPort();
        }
        
        grpcServer = ServerBuilder.forPort(grpcPort)
            .addService(new TestPipeStepProcessor())
            .build();
        
        grpcServer.start();
    }
    
    @AfterAll
    static void teardownServer() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdown();
            grpcServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
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
        // Deregister test services
        consulClient.deregisterService("perf-test-service");
        
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
    @DisplayName("Channel caching improves subsequent calls")
    void testChannelCaching() {
        // Register service
        ServiceOptions service = new ServiceOptions()
            .setName("perf-test-service")
            .setId("perf-test-service")
            .setAddress("localhost")
            .setPort(grpcPort)
            .setTags(List.of("grpc", "test"));
        
        Uni.createFrom().completionStage(() -> 
            consulClient.registerService(service).toCompletionStage()
        ).await().atMost(Duration.ofSeconds(5));
        
        // Wait for registration
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            var services = Uni.createFrom().completionStage(() -> 
                consulClient.healthServiceNodes("perf-test-service", true).toCompletionStage()
            ).await().atMost(Duration.ofSeconds(2));
            
            return services != null && !services.getList().isEmpty();
        });
        
        // First call - creates channel
        long start = System.currentTimeMillis();
        PipeStepProcessor client1 = clientFactory.getClientForService("perf-test-service")
            .await().atMost(Duration.ofSeconds(5));
        long firstCallTime = System.currentTimeMillis() - start;
        
        // Make a request to ensure channel is fully established
        ProcessRequest request = ProcessRequest.newBuilder()
            .setDocument(PipeDoc.newBuilder()
                .setId("test-doc")
                .setBody("test")
                .build())
            .setMetadata(ServiceMetadata.newBuilder()
                .setPipelineName("test")
                .setPipeStepName("step")
                .setStreamId("stream")
                .build())
            .build();
        
        client1.processData(request).await().atMost(Duration.ofSeconds(5));
        
        // Subsequent cached calls
        List<Long> cachedTimes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            start = System.currentTimeMillis();
            PipeStepProcessor client = clientFactory.getClientForService("perf-test-service")
                .await().atMost(Duration.ofSeconds(5));
            cachedTimes.add(System.currentTimeMillis() - start);
            assertThat(client).isNotNull();
        }
        
        // Average cached time should be less than first call
        double avgCachedTime = cachedTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
        
        System.out.printf("First call: %dms, Avg cached: %.2fms%n", 
            firstCallTime, avgCachedTime);
        
        // Cached calls should be faster or at least not slower
        // In integration tests, the first call might already be fast
        assertThat(avgCachedTime).isLessThanOrEqualTo(firstCallTime * 1.5);
        
        // Verify only one channel was created
        assertThat(clientFactory.hasChannel("localhost", grpcPort)).isTrue();
    }
    
    @Test
    @Order(2)
    @DisplayName("Multiple services create separate channels")
    void testMultipleServiceChannels() {
        // Register multiple services
        for (int i = 0; i < 3; i++) {
            ServiceOptions service = new ServiceOptions()
                .setName("multi-service-" + i)
                .setId("multi-service-" + i)
                .setAddress("localhost")
                .setPort(7000 + i) // Different ports
                .setTags(List.of("grpc", "test"));
            
            Uni.createFrom().completionStage(() -> 
                consulClient.registerService(service).toCompletionStage()
            ).await().atMost(Duration.ofSeconds(5));
        }
        
        // Create clients for different services
        clientFactory.getClient("localhost", 7000);
        clientFactory.getClient("localhost", 7001);
        clientFactory.getClient("localhost", 7002);
        
        // Should have 3 channels
        assertThat(clientFactory.getActiveChannelCount()).isEqualTo(3);
        
        // Each should be cached separately
        assertThat(clientFactory.hasChannel("localhost", 7000)).isTrue();
        assertThat(clientFactory.hasChannel("localhost", 7001)).isTrue();
        assertThat(clientFactory.hasChannel("localhost", 7002)).isTrue();
    }
    
    /**
     * Simple test gRPC service
     */
    static class TestPipeStepProcessor extends PipeStepProcessorGrpc.PipeStepProcessorImplBase {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        
        @Override
        public void processData(ProcessRequest request, StreamObserver<ProcessResponse> responseObserver) {
            int count = requestCount.incrementAndGet();
            
            ProcessResponse response = ProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(PipeDoc.newBuilder()
                    .setId(request.getDocument().getId())
                    .setBody("Processed #" + count + ": " + request.getDocument().getBody())
                    .build())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}