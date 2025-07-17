package com.rokkon.pipeline.consul.test;

import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import org.jboss.logging.Logger;

/**
 * Simple factory for creating ConsulClient instances in tests.
 * This bypasses all the CDI complexity and just creates a client with the given host/port.
 * Now uses DevServices-provided configuration instead of manual TestContainers.
 */
public class SimpleConsulClientFactory {
    
    private static final Logger LOG = Logger.getLogger(SimpleConsulClientFactory.class);
    
    private static ConsulClient testClient;
    private static Vertx vertx;
    
    public static synchronized ConsulClient getTestClient() {
        if (testClient == null) {
            LOG.info("Creating new test ConsulClient...");
            
            // Get configuration from system properties set by DevServices
            String host = System.getProperty("consul.host", "localhost");
            int port = Integer.parseInt(System.getProperty("consul.port", "8500"));
            
            LOG.infof("DevServices configuration: host=%s, port=%d", host, port);
            
            // Create Vertx if needed
            if (vertx == null) {
                LOG.info("Creating new Vertx instance...");
                vertx = Vertx.vertx();
                LOG.info("Vertx instance created");
            }
            
            // Create client with simple options
            LOG.info("Creating ConsulClientOptions...");
            ConsulClientOptions options = new ConsulClientOptions()
                    .setHost(host)
                    .setPort(port)
                    .setTimeout(10000); // 10 second timeout
            
            LOG.infof("ConsulClientOptions created: host=%s, port=%d, timeout=10000ms", 
                      options.getHost(), options.getPort());
            
            LOG.info("Creating ConsulClient...");
            testClient = ConsulClient.create(vertx, options);
            LOG.info("ConsulClient instance created");
            
            // Verify connection
            LOG.info("Verifying Consul connection...");
            try {
                var agentInfo = testClient.agentInfo().await().indefinitely();
                LOG.infof("Successfully connected to test Consul. Agent info: %s", agentInfo);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to connect to test Consul at %s:%d", host, port);
                LOG.error("Connection failure details:", e);
                throw new RuntimeException("Cannot connect to test Consul", e);
            }
        } else {
            LOG.info("Reusing existing test ConsulClient");
        }
        return testClient;
    }
    
    public static void cleanup() {
        if (testClient != null) {
            testClient.close();
            testClient = null;
        }
        if (vertx != null) {
            vertx.close();
            vertx = null;
        }
    }
}