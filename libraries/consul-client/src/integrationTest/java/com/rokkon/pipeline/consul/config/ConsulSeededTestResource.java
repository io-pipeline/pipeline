package com.rokkon.pipeline.consul.config;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Test resource that starts Consul with seeded configuration data.
 * Seeds data using HTTP API after container starts to handle complex JSON structures.
 */
public class ConsulSeededTestResource implements QuarkusTestResourceLifecycleManager {
    
    private static final Logger LOG = Logger.getLogger(ConsulSeededTestResource.class.getName());
    private static ConsulContainer consulContainer;
    
    @Override
    public Map<String, String> start() {
        LOG.info("Starting Consul container with seed data support...");
        
        // Start Consul container without seed commands (we'll seed via API)
        boolean reuse = Boolean.parseBoolean(System.getProperty("testcontainers.reuse.enable", "true"));
        consulContainer = new ConsulContainer(DockerImageName.parse("hashicorp/consul:1.21"))
            .withReuse(reuse);
        
        consulContainer.start();
        
        String host = consulContainer.getHost();
        int port = consulContainer.getMappedPort(8500);
        
        LOG.info("Consul started at: " + host + ":" + port);
        
        // Seed data via HTTP API
        seedConsulData(host, port);
        
        // Set system properties for DevServices compatibility
        System.setProperty("pipeline.consul.host", host);
        System.setProperty("pipeline.consul.port", String.valueOf(port));
        
        Map<String, String> config = new HashMap<>();
        config.put("quarkus.consul-config.enabled", "true");
        config.put("quarkus.consul-config.agent.host-port", host + ":" + port);
        config.put("quarkus.consul-config.fail-on-missing-key", "false");
        config.put("quarkus.consul-config.properties-value-keys", "config/application");
        config.put("pipeline.consul.host", host);
        config.put("pipeline.consul.port", String.valueOf(port));
        config.put("consul.host", host);
        config.put("consul.port", String.valueOf(port));
        
        return config;
    }
    
    private void seedConsulData(String host, int port) {
        try {
            // Create a standalone Vert.x instance for seeding
            io.vertx.mutiny.core.Vertx vertx = io.vertx.mutiny.core.Vertx.vertx();
            
            // Create Consul client options
            io.vertx.ext.consul.ConsulClientOptions options = new io.vertx.ext.consul.ConsulClientOptions()
                .setHost(host)
                .setPort(port)
                .setTimeout(5000);
            
            // Create Consul client
            io.vertx.mutiny.ext.consul.ConsulClient consulClient = 
                io.vertx.mutiny.ext.consul.ConsulClient.create(vertx, options);
            
            // Seed configuration data as JSON
            String configJson = """
                {
                    "test.config.property": "test-value-from-consul",
                    "test.config.number": "42",
                    "test.config.boolean": "true",
                    "pipeline.engine.name": "test-engine"
                }
                """;
            
            // Store the configuration in Consul KV
            consulClient.putValue("config/application", configJson)
                .await()
                .atMost(Duration.ofSeconds(10));
            
            LOG.info("Successfully seeded Consul with test configuration");
            
            // Close the Vert.x instance
            vertx.close();
            
        } catch (Exception e) {
            LOG.severe("Error seeding Consul data: " + e.getMessage());
            throw new RuntimeException("Failed to seed Consul data", e);
        }
    }
    
    @Override
    public void stop() {
        // Container lifecycle is managed by the reuse feature
        // Only stop if not reusable
        if (consulContainer != null) {
            // Check if reuse is enabled
            boolean reuse = Boolean.parseBoolean(System.getProperty("testcontainers.reuse.enable", "true"));
            if (!reuse) {
                consulContainer.stop();
            }
        }
    }
}