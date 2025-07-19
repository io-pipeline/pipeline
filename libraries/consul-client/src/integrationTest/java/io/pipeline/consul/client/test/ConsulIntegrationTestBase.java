package io.pipeline.consul.client.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Base class for integration tests that need both:
 * 1. Real Consul instance (for service discovery and health checks)
 * 2. WireMock server (for external service simulation)
 * 
 * Each test gets its own isolated Consul container and WireMock server.
 * This provides complete test isolation while maintaining realistic service discovery flows.
 */
public abstract class ConsulIntegrationTestBase {

    private static final Logger LOG = Logger.getLogger(ConsulIntegrationTestBase.class);
    
    // Per-test Consul container
    protected GenericContainer<?> consulContainer;
    protected ConsulClient consulClient;
    protected String consulHost;
    protected int consulPort;
    
    // Per-test WireMock server
    protected WireMockServer wireMockServer;
    protected WireMock wireMock;
    protected String wireMockHost;
    protected int wireMockPort;
    
    // Vertx instance for Consul client
    protected Vertx vertx;

    @BeforeEach
    void setupPerTestEnvironment() throws Exception {
        LOG.info("Setting up per-test Consul container and WireMock server");
        
        // Setup Vertx
        vertx = Vertx.vertx();
        
        // 1. Start fresh Consul container for this test
        setupConsulContainer();
        
        // 2. Start fresh WireMock server for this test
        setupWireMockServer();
        
        // 3. Setup basic mock responses for health checks
        setupDefaultMockResponses();
        
        // 4. Allow subclasses to customize setup
        customizeTestSetup();
    }

    @AfterEach
    void teardownPerTestEnvironment() {
        LOG.info("Tearing down per-test environment");
        
        try {
            // Allow subclasses to cleanup
            customizeTestTeardown();
            
            // Stop WireMock server
            if (wireMockServer != null) {
                wireMockServer.stop();
                wireMockServer = null;
            }
            
            // Stop Consul container
            if (consulContainer != null) {
                consulContainer.stop();
                consulContainer = null;
            }
            
            // Close Vertx
            if (vertx != null) {
                vertx.closeAndAwait();
                vertx = null;
            }
        } catch (Exception e) {
            LOG.error("Error during test teardown", e);
        }
    }

    /**
     * Sets up a fresh Consul container for this test
     */
    private void setupConsulContainer() throws Exception {
        LOG.info("Starting fresh Consul container for test");
        
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
        
        LOG.infof("Consul container started at %s:%d", consulHost, consulPort);
    }

    /**
     * Sets up a fresh WireMock server for this test
     */
    private void setupWireMockServer() {
        LOG.info("Starting fresh WireMock server for test");
        
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
            .dynamicPort()
            .notifier(new WireMockNotifier()));
        
        wireMockServer.start();
        
        wireMockHost = "localhost";
        wireMockPort = wireMockServer.port();
        
        // Create WireMock client
        wireMock = new WireMock(wireMockPort);
        WireMock.configureFor(wireMockPort);
        
        LOG.infof("WireMock server started at %s:%d", wireMockHost, wireMockPort);
    }

    /**
     * Sets up default mock responses for health checks and common endpoints
     */
    private void setupDefaultMockResponses() {
        LOG.info("Setting up default mock responses");
        
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
        
        wireMock.register(get(urlPathMatching("/ready"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")));
        
        // Mock root endpoint for connectivity checks
        wireMock.register(get(urlPathMatching("/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("Mock Service Running")));
    }

    /**
     * Registers a fake module in Consul that points to our WireMock server.
     * This creates the complete integration test scenario:
     * 1. Service is registered in Consul service catalog
     * 2. Consul health checks the service (hits WireMock)
     * 3. Application discovers the service via Consul
     * 4. Application calls the service (hits WireMock)
     */
    protected void registerFakeModuleInConsul(String serviceName, String serviceType, String version) {
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
            CheckOptions checkOptions = new CheckOptions()
                .setName("Module Health Check")
                .setGrpc(wireMockHost + ":" + wireMockPort + "/grpc.health.v1.Health")
                .setInterval("10s")
                .setDeregisterAfter("30s");
            
            serviceOptions.setCheckOptions(checkOptions);
            
            // Register with Consul
            consulClient.registerService(serviceOptions).await().indefinitely();
            
            // Wait a bit for health check to pass
            Thread.sleep(2000);
            
            LOG.infof("Successfully registered fake module '%s' in Consul", serviceName);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to register fake module in Consul", e);
        }
    }

    /**
     * Verifies that a service is registered and healthy in Consul
     */
    protected void verifyServiceInConsul(String serviceName) {
        try {
            var services = consulClient.catalogServices().await().indefinitely();
            if (!services.getList().stream().anyMatch(s -> s.getName().equals(serviceName))) {
                throw new AssertionError("Service " + serviceName + " not found in Consul catalog");
            }
            
            var healthChecks = consulClient.healthServiceNodes(serviceName, true).await().indefinitely();
            if (healthChecks.getList().isEmpty()) {
                throw new AssertionError("No healthy instances of service " + serviceName + " found in Consul");
            }
            
            LOG.infof("Service '%s' is registered and healthy in Consul", serviceName);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify service in Consul", e);
        }
    }

    /**
     * Returns the configuration that should be used for the Quarkus application
     * to connect to our per-test Consul and WireMock instances.
     */
    protected Map<String, String> getTestConfiguration() {
        return Map.of(
            // Real Consul configuration
            "consul.host", consulHost,
            "consul.port", String.valueOf(consulPort),
            "quarkus.consul-config.enabled", "false", // Disable to avoid config cruft
            
            // WireMock configuration for external services
            "quarkus.grpc.clients.registration-service.host", wireMockHost,
            "quarkus.grpc.clients.registration-service.port", String.valueOf(wireMockPort),
            "quarkus.grpc.clients.registration-service.plain-text", "true",
            
            // Test-specific settings
            "pipeline.module.connection.validator", "test"
        );
    }

    /**
     * Override this method to customize the test setup
     */
    protected void customizeTestSetup() throws Exception {
        // Default: no additional setup
    }

    /**
     * Override this method to customize the test teardown
     */
    protected void customizeTestTeardown() throws Exception {
        // Default: no additional teardown
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