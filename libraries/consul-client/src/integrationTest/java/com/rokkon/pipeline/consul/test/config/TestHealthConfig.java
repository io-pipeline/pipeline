package com.rokkon.pipeline.consul.test.config;

import com.rokkon.pipeline.consul.config.ConsulConfigSource.ConsulConfig.HealthConfig;
import java.time.Duration;

/**
 * Test implementation of HealthConfig for integration tests.
 */
public class TestHealthConfig implements HealthConfig {
    private final Duration checkInterval;
    private final Duration deregisterAfter;
    private final Duration timeout;
    
    public TestHealthConfig() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(5));
    }
    
    public TestHealthConfig(Duration checkInterval, Duration deregisterAfter, Duration timeout) {
        this.checkInterval = checkInterval;
        this.deregisterAfter = deregisterAfter;
        this.timeout = timeout;
    }
    
    @Override
    public Duration checkInterval() {
        return checkInterval;
    }
    
    @Override
    public Duration deregisterAfter() {
        return deregisterAfter;
    }
    
    @Override
    public Duration timeout() {
        return timeout;
    }
}