package io.pipeline.consul.client.integration;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that configures the application to use the in-memory module registry
 * instead of Consul for module existence checks, while still using Consul for KV operations.
 */
public class InMemoryRegistryTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Configure to use in-memory registry for module existence checks
            "pipeline.module-registry.type", "memory",
            
            // Enable basic modules in the in-memory registry  
            "pipeline.module-registry.basic-modules.enabled", "true",
            "pipeline.module-registry.basic-modules.list", "filesystem,echo,test-harness,parser,chunker,embedder,open-search,test-module",
            
            // Consul is still used for KV operations, configure for localhost
            "quarkus.consul.agent.host-port", "localhost:8500",
            "pipeline.consul.kv-prefix", "pipeline-test",
            
            // Logging levels for debugging
            "quarkus.log.category.\"io.pipeline.consul.client.service.ModuleWhitelistServiceImpl\".level", "DEBUG",
            "quarkus.log.category.\"io.pipeline.consul.client.service.registry\".level", "DEBUG"
        );
    }

    @Override
    public String getConfigProfile() {
        return "integration-test";
    }
}