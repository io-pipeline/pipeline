package io.pipeline.consul.client.service;

import io.pipeline.consul.client.test.ConsulClientTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile for ModuleRegistryService unit tests.
 * Provides test configuration values for the ConsulConfigSource.
 */
public class GlobalModuleRegistryTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        // Configure ConsulClientTestProfile for this test
        ConsulClientTestProfile.TestConfiguration baseConfig = new ConsulClientTestProfile.TestConfiguration().withoutConsul();
        
        Map<String, String> config = new java.util.HashMap<>(baseConfig.getProperties());
        
        // Add ConsulConfigSource test values
        config.put("rokkon.consul.health.check-interval", "10s");
        config.put("rokkon.consul.health.deregister-after", "60s");
        config.put("rokkon.consul.health.timeout", "5s");
        
        return config;
    }
}