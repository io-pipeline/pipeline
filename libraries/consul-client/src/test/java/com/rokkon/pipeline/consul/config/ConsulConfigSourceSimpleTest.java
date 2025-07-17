package com.rokkon.pipeline.consul.config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple tests for configuration properties.
 * These test that our configuration structure is properly defined
 * and that default values are loaded from application.yml.
 *
 * This is a unit test that does not require Consul to be running.
 */
@QuarkusTest
@TestProfile(SimpleConfigTestProfile.class)
@DisplayName("Configuration Property Tests")
@Tag("config-tests") // Tag for running separately if needed
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsulConfigSourceSimpleTest {

    // Test individual properties to verify they're loaded
    @ConfigProperty(name = "pipeline.engine.grpc-port")
    int grpcPort;

    @ConfigProperty(name = "pipeline.engine.rest-port")
    int restPort;

    @ConfigProperty(name = "pipeline.consul.cleanup.enabled")
    boolean cleanupEnabled;

    @ConfigProperty(name = "pipeline.consul.cleanup.interval")
    Duration cleanupInterval;

    @ConfigProperty(name = "pipeline.modules.service-prefix")
    String servicePrefix;

    @ConfigProperty(name = "pipeline.default-cluster.name")
    String defaultClusterName;

    @Test
    @DisplayName("Should load engine configuration properties")
    void testEngineProperties() {
        assertEquals(49000, grpcPort);
        assertEquals(8080, restPort);
    }

    @Test
    @DisplayName("Should load consul cleanup configuration")
    void testConsulCleanupProperties() {
        assertTrue(cleanupEnabled);
        assertEquals(Duration.ofMinutes(5), cleanupInterval);
    }

    @Test
    @DisplayName("Should load module configuration")
    void testModuleProperties() {
        assertEquals("module-", servicePrefix);
    }

    @Test
    @DisplayName("Should load default cluster configuration")
    void testDefaultClusterProperties() {
        assertEquals("default", defaultClusterName);
    }
}