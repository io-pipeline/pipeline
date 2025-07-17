package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.testing.util.UnifiedTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile for GlobalModuleRegistryService unit tests.
 * Provides test configuration values for the ConsulConfigSource.
 */
public class GlobalModuleRegistryTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new java.util.HashMap<>(new UnifiedTestProfile().getConfigOverrides());
        
        // Add ConsulConfigSource test values
        config.put("rokkon.consul.health.check-interval", "10s");
        config.put("rokkon.consul.health.deregister-after", "60s");
        config.put("rokkon.consul.health.timeout", "5s");
        
        return config;
    }
}