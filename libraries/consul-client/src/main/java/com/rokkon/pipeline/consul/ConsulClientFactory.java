package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.consul.config.ConsulConfiguration;
import io.vertx.mutiny.core.Vertx;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Factory for creating Consul clients.
 * This is the real implementation used in production and integration tests.
 */
@ApplicationScoped
public class ConsulClientFactory {

    private static final Logger LOG = Logger.getLogger(ConsulClientFactory.class);

    @Inject
    ConsulConfiguration consulConfig;

    /**
     * Default constructor for CDI.
     */
    public ConsulClientFactory() {
        // Default constructor for CDI
    }

    @PostConstruct
    void init() {
        LOG.infof("ConsulClientFactory initialized with config - host: %s, port: %d", 
                  consulConfig.host(), consulConfig.port());

        // Debug: Check all possible configuration sources
        LOG.debugf("System property consul.host: %s", System.getProperty("consul.host"));
        LOG.debugf("System property consul.port: %s", System.getProperty("consul.port"));
        LOG.debugf("Config value consul.host: %s", consulConfig.host());
        LOG.debugf("Config value consul.port: %d", consulConfig.port());
    }

    /**
     * Creates and configures a Consul client based on the application configuration.
     * This method is a CDI producer that creates a singleton ConsulClient instance.
     * The client is configured with the host, port, SSL settings, and timeout from the application configuration.
     * 
     * @param vertx The Vertx instance to use for creating the Consul client
     * @return A configured ConsulClient instance
     */
    @Produces
    @ApplicationScoped
    public ConsulClient consulClient(Vertx vertx) {
        // Log configuration source
        String host = consulConfig.host();
        int port = consulConfig.port();

        LOG.infof("Creating Consul client with configuration - host: %s, port: %d", host, port);
        LOG.infof("Configuration source: consul.host=%s, consul.port=%s", 
                  System.getProperty("consul.host", "not set"), 
                  System.getProperty("consul.port", "not set"));
        LOG.infof("Creating Consul client connecting to %s:%d (secure: %s, trustAll: %s)", 
                  host, port, consulConfig.secure(), consulConfig.trustAll());
        LOG.debugf("Full Consul client options - timeout: %dms", consulConfig.timeout().toMillis());

        ConsulClientOptions options = new ConsulClientOptions()
                .setHost(host)
                .setPort(port)
                .setSsl(consulConfig.secure())
                .setTrustAll(consulConfig.trustAll())
                .setTimeout((int) consulConfig.timeout().toMillis());

        // Add ACL token if provided
        consulConfig.token().ifPresent(token -> {
            LOG.debug("Configuring Consul client with ACL token");
            options.setAclToken(token);
        });

        ConsulClient client = ConsulClient.create(vertx, options);

        // Test the connection immediately
        client.agentInfo()
            .onItem().invoke(info -> {
                JsonObject config = info.getJsonObject("Config", new JsonObject());
                String version = config.getString("Version", "unknown");
                LOG.infof("Successfully connected to Consul at %s:%d. Agent version: %s", host, port, version);
            })
            .onFailure().invoke(throwable -> {
                // Only log at debug level if this happens during shutdown
                String errorMsg = throwable.getMessage();
                if (errorMsg != null && errorMsg.contains("Error Occurred After Shutdown")) {
                    LOG.debug("Consul connection test failed during shutdown");
                } else {
                    LOG.errorf("Failed to connect to Consul at %s:%d - %s", host, port, throwable.getMessage());
                }
            })
            .subscribe().with(
                info -> {},
                throwable -> {
                    // Only log at debug level if this happens during shutdown  
                    String errorMsg = throwable.getMessage();
                    if (errorMsg != null && errorMsg.contains("Error Occurred After Shutdown")) {
                        LOG.debug("Consul connection test failed during shutdown");
                    } else {
                        LOG.errorf("Consul connection test failed: %s", throwable.getMessage());
                    }
                }
            );

        return client;
    }
}
