package io.pipeline.consul.client.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Proof of concept integration test using a single Consul container
 * with WireMock for external service simulation.
 * 
 * This test demonstrates:
 * 1. Single Consul container for the entire test class
 * 2. WireMock server for external service simulation
 * 3. Consul service registration pointing to WireMock
 * 4. Health checks from Consul to WireMock
 * 5. Complete end-to-end integration flow
 * 
 * Next step: Figure out per-test isolation using Consul datacenters
 */
class SingleConsulContainerIT {

    private static final Logger LOG = Logger.getLogger(SingleConsulContainerIT.class);
    
    // Shared Consul container for all tests
    private static GenericContainer<?> consulContainer;
    private static ConsulClient consulClient;
    private static String consulHost;
    private static int consulPort;
    
    // Per-test WireMock server
    private WireMockServer wireMockServer;
    private WireMock wireMock;
    private String wireMockHost;
    private int wireMockPort;
    
    // Vertx instance
    private static Vertx vertx;

    @BeforeAll
    static void setupSharedConsul() throws Exception {
        LOG.info("Setting up shared Consul container for all tests");
        
        vertx = Vertx.vertx();
        
        // Start single Consul container for all tests
        consulContainer = new GenericContainer<>(DockerImageName.parse("hashicorp/consul:1.21"))
            .withExposedPorts(8500)
            .withCommand("agent", "-server", "-bootstrap", "-ui", "-client", "0.0.0.0")
            .waitingFor(Wait.forLogMessage(".*cluster leadership acquired.*", 1)
                .withStartupTimeout(Duration.ofSeconds(30)));
        
        consulContainer.start();
        
        // Wait for Consul to be ready
        CountDownLatch latch = new CountDownLatch(1);
        consulContainer.followOutput(frame -> {
            if (frame.getUtf8String().contains("cluster leadership acquired")) {
                latch.countDown();
            }
        });
        
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Consul container did not start within timeout");
        }
        
        // Setup Consul client
        consulHost = consulContainer.getHost();
        consulPort = consulContainer.getMappedPort(8500);
        
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
        
        consulClient = ConsulClient.create(vertx, options);
        
        LOG.infof("Shared Consul container started at %s:%d", consulHost, consulPort);
    }

    @AfterAll
    static void teardownSharedConsul() {
        LOG.info("Tearing down shared Consul container");
        
        try {
            if (consulContainer != null) {
                consulContainer.stop();
                consulContainer = null;
            }
            
            if (vertx != null) {
                vertx.closeAndAwait();
                vertx = null;
            }
        } catch (Exception e) {
            LOG.error("Error during shared teardown", e);
        }
    }

    @BeforeEach
    void setupPerTestWireMock() {
        LOG.info("Setting up per-test WireMock server");
        
        // Start fresh WireMock server for each test
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
            .dynamicPort()
            .notifier(new WireMockNotifier()));
        
        wireMockServer.start();
        
        wireMockHost = "localhost";
        wireMockPort = wireMockServer.port();
        
        // Create WireMock client
        wireMock = new WireMock(wireMockPort);
        WireMock.configureFor(wireMockPort);
        
        // Setup default mock responses
        setupDefaultMockResponses();
        
        LOG.infof("WireMock server started at %s:%d", wireMockHost, wireMockPort);
    }

    @Test
    void testCompleteIntegrationFlow() throws Exception {
        LOG.info("Testing complete integration flow: Consul + WireMock");
        
        // 1. Register a fake module in Consul that points to WireMock
        String serviceName = "test-module-" + System.currentTimeMillis();
        registerFakeModuleInConsul(serviceName, "test-connector", "1.0.0");
        
        // 2. Wait for health check to pass
        Thread.sleep(3000);
        
        // 3. Verify service is registered and healthy in Consul
        verifyServiceInConsul(serviceName);
        
        // 4. Verify that we can discover the service via Consul
        var services = consulClient.catalogServices().await().indefinitely();
        assertTrue(services.getList().stream().anyMatch(s -> s.getName().equals(serviceName)), 
            "Service should be discoverable via Consul");
        
        // 5. Verify that health checks are working (Consul → WireMock)
        var healthyServices = consulClient.healthServiceNodes(serviceName, true).await().indefinitely();
        assertFalse(healthyServices.getList().isEmpty(), 
            "Service should have healthy instances");
        
        // 6. Verify that we can call the service (Application → WireMock)
        // This simulates what would happen when the application discovers and calls the service
        var serviceEntry = healthyServices.getList().get(0);
        String serviceHost = serviceEntry.getService().getAddress();
        int servicePort = serviceEntry.getService().getPort();
        
        // Make a direct call to the service (via WireMock)
        given()
            .when()
            .get("http://" + serviceHost + ":" + servicePort + "/health")
            .then()
            .statusCode(200)
            .body(containsString("UP"));
        
        LOG.info("✅ Complete integration flow successful!");
        LOG.info("   - Service registered in Consul ✓");
        LOG.info("   - Health checks passing (Consul → WireMock) ✓");
        LOG.info("   - Service discoverable via Consul ✓");
        LOG.info("   - Service callable (Application → WireMock) ✓");
    }

    @Test
    void testMultipleServicesIsolation() throws Exception {
        LOG.info("Testing that multiple services can coexist");
        
        // Register multiple fake services
        String service1 = "test-service-1-" + System.currentTimeMillis();
        String service2 = "test-service-2-" + System.currentTimeMillis();
        
        registerFakeModuleInConsul(service1, "connector", "1.0.0");
        registerFakeModuleInConsul(service2, "pipeline", "2.0.0");
        
        // Wait for health checks
        Thread.sleep(3000);
        
        // Both services should be healthy
        verifyServiceInConsul(service1);
        verifyServiceInConsul(service2);
        
        // Both should be discoverable
        var services = consulClient.catalogServices().await().indefinitely();
        assertTrue(services.getList().stream().anyMatch(s -> s.getName().equals(service1)));
        assertTrue(services.getList().stream().anyMatch(s -> s.getName().equals(service2)));
        
        LOG.info("✅ Multiple services coexist successfully!");
    }

    private void setupDefaultMockResponses() {
        // Mock gRPC health check endpoint (for Consul health checking)
        wireMock.register(post(urlPathMatching("/grpc.health.v1.Health/Check"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/grpc")
                .withBody("serving".getBytes())));
        
        // Mock HTTP health check endpoint
        wireMock.register(get(urlPathMatching("/health.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"UP\",\"checks\":[]}")));
        
        // Mock common health endpoints
        wireMock.register(get(urlPathMatching("/healthz"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")));
    }

    private void registerFakeModuleInConsul(String serviceName, String serviceType, String version) {
        LOG.infof("Registering fake module '%s' in Consul, pointing to WireMock", serviceName);
        
        try {
            // Register service in Consul service catalog
            // Use host.docker.internal for service address so Consul can reach WireMock
            String serviceAddress = "host.docker.internal";
            ServiceOptions serviceOptions = new ServiceOptions()
                .setId(serviceName + "-" + System.currentTimeMillis()) // Unique ID
                .setName(serviceName)
                .setAddress(serviceAddress)
                .setPort(wireMockPort)
                .setTags(List.of(
                    "grpc",
                    "pipeline-module",
                    "type:" + serviceType,
                    "version:" + version
                ))
                .setMeta(Map.of(
                    "service-type", serviceType,
                    "version", version,
                    "grpc-enabled", "true"
                ));
            
            // Add health check that points to WireMock
            // Use host.docker.internal to allow Consul container to reach WireMock on host
            String healthCheckUrl = "http://host.docker.internal:" + wireMockPort + "/health";
            CheckOptions checkOptions = new CheckOptions()
                .setName("Module Health Check")
                .setHttp(healthCheckUrl)
                .setInterval("5s")
                .setDeregisterAfter("15s");
            
            serviceOptions.setCheckOptions(checkOptions);
            
            // Register with Consul
            consulClient.registerService(serviceOptions).await().indefinitely();
            
            LOG.infof("Successfully registered fake module '%s' in Consul", serviceName);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to register fake module in Consul", e);
        }
    }

    private void verifyServiceInConsul(String serviceName) {
        try {
            var services = consulClient.catalogServices().await().indefinitely();
            assertTrue(services.getList().stream().anyMatch(s -> s.getName().equals(serviceName)), 
                "Service " + serviceName + " should be in Consul catalog");
            
            var healthChecks = consulClient.healthServiceNodes(serviceName, true).await().indefinitely();
            assertFalse(healthChecks.getList().isEmpty(), 
                "Service " + serviceName + " should have healthy instances");
            
            LOG.infof("Service '%s' is registered and healthy in Consul", serviceName);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify service in Consul", e);
        }
    }

    /**
     * Custom WireMock notifier that uses JBoss logging
     */
    private static class WireMockNotifier implements com.github.tomakehurst.wiremock.common.Notifier {
        private static final Logger LOG = Logger.getLogger("WireMock");

        @Override
        public void info(String message) {
            LOG.debug(message);
        }

        @Override
        public void error(String message) {
            LOG.error(message);
        }

        @Override
        public void error(String message, Throwable t) {
            LOG.error(message, t);
        }
    }
}