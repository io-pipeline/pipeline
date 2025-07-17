package com.rokkon.pipeline.consul.test.config;

import com.rokkon.pipeline.consul.config.ConsulConfigSource.ConsulConfig.CleanupConfig;
import java.time.Duration;

/**
 * Test implementation of CleanupConfig for integration tests.
 */
public class TestCleanupConfig implements CleanupConfig {
    private final boolean enabled;
    private final Duration interval;
    private final Duration zombieThreshold;
    private final boolean cleanupStaleWhitelist;
    
    public TestCleanupConfig() {
        this(false, Duration.ofMinutes(5), Duration.ofMinutes(2), false);
    }
    
    public TestCleanupConfig(boolean enabled, Duration interval, Duration zombieThreshold, boolean cleanupStaleWhitelist) {
        this.enabled = enabled;
        this.interval = interval;
        this.zombieThreshold = zombieThreshold;
        this.cleanupStaleWhitelist = cleanupStaleWhitelist;
    }
    
    @Override
    public boolean enabled() {
        return enabled;
    }
    
    @Override
    public Duration interval() {
        return interval;
    }
    
    @Override
    public Duration zombieThreshold() {
        return zombieThreshold;
    }
    
    @Override
    public boolean cleanupStaleWhitelist() {
        return cleanupStaleWhitelist;
    }
}