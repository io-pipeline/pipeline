package com.rokkon.pipeline.consul.test.config;

import com.rokkon.pipeline.consul.config.ConsulConfigSource.ModulesConfig;
import java.time.Duration;

/**
 * Test implementation of ModulesConfig for integration tests.
 */
public class TestModulesConfig implements ModulesConfig {
    private final boolean autoDiscover;
    private final String servicePrefix;
    private final boolean requireWhitelist;
    private final Duration connectionTimeout;
    private final int maxInstancesPerModule;
    
    public TestModulesConfig() {
        this(false, "module-", true, Duration.ofSeconds(30), 10);
    }
    
    public TestModulesConfig(boolean autoDiscover, String servicePrefix, boolean requireWhitelist, 
                             Duration connectionTimeout, int maxInstancesPerModule) {
        this.autoDiscover = autoDiscover;
        this.servicePrefix = servicePrefix;
        this.requireWhitelist = requireWhitelist;
        this.connectionTimeout = connectionTimeout;
        this.maxInstancesPerModule = maxInstancesPerModule;
    }
    
    @Override
    public boolean autoDiscover() {
        return autoDiscover;
    }
    
    @Override
    public String servicePrefix() {
        return servicePrefix;
    }
    
    @Override
    public boolean requireWhitelist() {
        return requireWhitelist;
    }
    
    @Override
    public Duration connectionTimeout() {
        return connectionTimeout;
    }
    
    @Override
    public int maxInstancesPerModule() {
        return maxInstancesPerModule;
    }
}