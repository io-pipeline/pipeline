package com.rokkon.pipeline.consul.config;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Simple test profile for configuration property tests.
 * This profile provides the configuration values that the test expects.
 */
public class SimpleConfigTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Engine configuration
            "pipeline.engine.grpc-port", "49000",
            "pipeline.engine.rest-port", "8080",
            
            // Consul cleanup configuration
            "pipeline.consul.cleanup.enabled", "true",
            "pipeline.consul.cleanup.interval", "5m",
            
            // Module configuration
            "pipeline.modules.service-prefix", "module-",
            
            // Default cluster configuration
            "pipeline.default-cluster.name", "default",
            
            // Disable consul-config for unit tests
            "quarkus.consul-config.enabled", "false"
        );
    }
}