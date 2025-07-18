package io.pipeline.consul.client.test.config;

import io.pipeline.consul.client.config.ConsulConfigSource.ConsulConfig;

/**
 * Test implementation of ConsulConfig for integration tests.
 */
public class TestConsulConfig implements ConsulConfig {
    private final CleanupConfig cleanupConfig;
    private final HealthConfig healthConfig;
    
    public TestConsulConfig() {
        this(new TestCleanupConfig(), new TestHealthConfig());
    }
    
    public TestConsulConfig(CleanupConfig cleanupConfig, HealthConfig healthConfig) {
        this.cleanupConfig = cleanupConfig;
        this.healthConfig = healthConfig;
    }
    
    @Override
    public CleanupConfig cleanup() {
        return cleanupConfig;
    }
    
    @Override
    public HealthConfig health() {
        return healthConfig;
    }
}