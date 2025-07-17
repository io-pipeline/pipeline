package com.rokkon.pipeline.consul.test;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.awaitility.Awaitility;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JUnit 5 extension that manages Consul lifecycle for integration tests.
 * 
 * This extension:
 * - Checks if Consul is already running, if not starts Docker Compose
 * - Provides namespace isolation per test class
 * - Cleans up namespaces before/after tests based on configuration
 */
public class ConsulTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConsulTestExtension.class);
    private static final Lock STARTUP_LOCK = new ReentrantLock();
    private static volatile ComposeContainer environment;
    private static volatile boolean initialized = false;
    
    // Store namespace contexts per test class
    private static final ConcurrentHashMap<Class<?>, String> namespaces = new ConcurrentHashMap<>();
    
    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        STARTUP_LOCK.lock();
        try {
            if (!initialized) {
                ensureConsulIsRunning();
                initialized = true;
            }
        } finally {
            STARTUP_LOCK.unlock();
        }
        
        // Store namespace for this test class
        Class<?> testClass = extensionContext.getRequiredTestClass();
        ConsulIntegrationTest annotation = testClass.getAnnotation(ConsulIntegrationTest.class);
        
        String namespace = annotation.namespacePrefix().isEmpty() 
            ? "test/" + testClass.getSimpleName() + "/"
            : annotation.namespacePrefix() + "/";
            
        namespaces.put(testClass, namespace);
    }
    
    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        ConsulIntegrationTest annotation = testClass.getAnnotation(ConsulIntegrationTest.class);
        
        if (annotation.cleanup()) {
            String namespace = namespaces.get(testClass);
            if (namespace != null) {
                LOG.debug("Cleaning namespace {} before test {}", 
                    namespace, extensionContext.getDisplayName());
                cleanNamespace(namespace);
            }
        }
    }
    
    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        // Optional: could add cleanup after each test if needed
    }
    
    private void ensureConsulIsRunning() {
        // First check if Consul is already available
        if (isConsulAvailable()) {
            LOG.info("Consul is already running at localhost:8500");
            System.setProperty("consul.host", "localhost");
            System.setProperty("consul.port", "8500");
            return;
        }
        
        LOG.info("Consul not available, starting Docker Compose environment");
        startDockerCompose();
    }
    
    private boolean isConsulAvailable() {
        try {
            ConsulClientOptions options = new ConsulClientOptions()
                .setHost("localhost")
                .setPort(8500);
                
            ConsulClient client = ConsulClient.create(Vertx.vertx(), options);
            
            // Try to get agent info to check connection
            var agentInfo = client.agentInfo()
                .await()
                .atMost(Duration.ofSeconds(2));
                
            return agentInfo != null;
        } catch (Exception e) {
            LOG.debug("Consul not available: {}", e.getMessage());
            return false;
        }
    }
    
    private void startDockerCompose() {
        File composeFile = new File("docker/test-compose.yml");
        if (!composeFile.exists()) {
            // Fall back to the main compose file if test-specific doesn't exist
            composeFile = new File("docker/pipeline-stack.yml");
        }
        
        if (!composeFile.exists()) {
            throw new IllegalStateException("No Docker Compose file found. Expected at docker/test-compose.yml or docker/pipeline-stack.yml");
        }
        
        environment = new ComposeContainer(composeFile)
            .withExposedService("consul", 8500, 
                Wait.forHttp("/v1/status/leader")
                    .forPort(8500)
                    .forStatusCode(200))
            .withLocalCompose(true); // Use local docker-compose command
            
        environment.start();
        
        // Set system properties for test configuration
        System.setProperty("consul.host", "localhost");
        System.setProperty("consul.port", 
            String.valueOf(environment.getServicePort("consul", 8500)));
        
        LOG.info("Consul started at localhost:{}", 
            environment.getServicePort("consul", 8500));
            
        // Wait for Consul to be fully ready
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .until(this::isConsulAvailable);
            
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (environment != null) {
                LOG.info("Stopping Docker Compose environment");
                environment.stop();
            }
        }));
    }
    
    private void cleanNamespace(String namespace) {
        try {
            ConsulClientOptions options = new ConsulClientOptions()
                .setHost(System.getProperty("consul.host", "localhost"))
                .setPort(Integer.parseInt(System.getProperty("consul.port", "8500")));
                
            ConsulClient client = ConsulClient.create(Vertx.vertx(), options);
            
            // Delete all keys in namespace
            client.deleteValues(namespace)
                .await()
                .atMost(Duration.ofSeconds(5));
                
        } catch (Exception e) {
            LOG.warn("Error cleaning namespace {}: {}", namespace, e.getMessage());
        }
    }
    
    /**
     * Get the namespace for a test class.
     */
    public static String getNamespace(Class<?> testClass) {
        return namespaces.get(testClass);
    }
}