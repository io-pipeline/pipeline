package com.rokkon.pipeline.consul.profile;

import com.rokkon.pipeline.consul.util.ConsulDevServicesUtil;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that enables Consul config and maps DevServices properties correctly.
 * This profile is used for integration tests that need to connect to the actual Consul instance.
 */
public class WithConsulConfigProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        
        // Get the actual Consul host and port from the running container
        String[] hostPort = ConsulDevServicesUtil.getConsulHostPort();
        String consulHost = hostPort[0];
        String consulPort = hostPort[1];
        
        System.out.println("WithConsulConfigProfile: Found Consul at " + consulHost + ":" + consulPort);
        
        // Enable Consul config
        config.put("quarkus.consul-config.enabled", "true");
        
        // Map to consul.* properties for ConsulClientFactory
        config.put("consul.host", consulHost);
        config.put("consul.port", consulPort);
        
        // Also configure quarkus.consul-config to use the same connection
        config.put("quarkus.consul-config.agent.host-port", consulHost + ":" + consulPort);
        
        // Basic pipeline config
        config.put("pipeline.consul.kv-prefix", "test-pipeline");
        
        // Disable config validation
        config.put("quarkus.configuration.build-time-mismatch-at-runtime", "warn");
        config.put("smallrye.config.validate", "false");
        
        // Disable module auto-registration
        config.put("module.auto-register.enabled", "false");
        
        // Configure gRPC client for registration service (even if not used)
        config.put("quarkus.grpc.clients.registration-service.host", "localhost");
        config.put("quarkus.grpc.clients.registration-service.port", "39100");
        config.put("quarkus.grpc.clients.registration-service.plain-text", "true");
        
        return config;
    }
}