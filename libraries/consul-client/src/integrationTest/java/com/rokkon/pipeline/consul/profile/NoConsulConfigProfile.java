package com.rokkon.pipeline.consul.profile;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile that disables Consul config to test REST endpoints independently.
 */
public class NoConsulConfigProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        // Get the DevServices-provided Consul port
        String consulPort = System.getProperty("pipeline.consul.port", "8500");
        String consulHost = System.getProperty("pipeline.consul.host", "localhost");
        
        return Map.of(
            // Disable Consul config
            "quarkus.consul-config.enabled", "false",
            
            // Map pipeline.consul.* to consul.* for ConsulClientFactory
            "consul.host", consulHost,
            "consul.port", consulPort,
            
            // Basic pipeline config
            "pipeline.consul.kv-prefix", "test-pipeline",
            
            // Disable config validation
            "quarkus.configuration.build-time-mismatch-at-runtime", "warn",
            "smallrye.config.validate", "false"
        );
    }
}