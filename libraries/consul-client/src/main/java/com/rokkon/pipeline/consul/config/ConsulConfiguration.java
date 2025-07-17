package com.rokkon.pipeline.consul.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration for Consul client connection.
 * These properties control how the application connects to Consul.
 */
@ConfigMapping(prefix = "consul")
public interface ConsulConfiguration {

    /**
     * The host where Consul is running.
     * 
     * @return The hostname or IP address of the Consul server
     */
    @WithDefault("localhost")
    String host();

    /**
     * The port where Consul is running.  
     * 
     * @return The port number of the Consul server
     */
    @WithDefault("8500")
    int port();

    /**
     * Whether to use HTTPS for Consul connection.
     * 
     * @return True if HTTPS should be used for Consul connection, false otherwise
     */
    @WithDefault("false")
    boolean secure();

    /**
     * The timeout for Consul operations.
     * 
     * @return The duration to wait before timing out Consul operations
     */
    @WithDefault("10s")
    Duration timeout();

    /**
     * Optional ACL token for Consul authentication.
     * 
     * @return An Optional containing the ACL token for Consul authentication, or empty if not provided
     */
    Optional<String> token();


    /**
     * Whether to trust all certificates (only for development/testing).
     * 
     * @return True if all SSL certificates should be trusted, false otherwise
     */
    @WithDefault("false")
    boolean trustAll();

    /**
     * The datacenter name (optional).
     * 
     * @return An Optional containing the datacenter name, or empty if not provided
     */
    Optional<String> datacenter();

    /**
     * KV store configuration.
     * 
     * @return The KV store configuration
     */
    Kv kv();

    /**
     * Interface for Consul KV store configuration.
     * Contains settings for KV store operations.
     */
    interface Kv {
        /**
         * The prefix for KV store operations.
         * 
         * @return The prefix string used for KV store operations
         */
        @WithDefault("pipeline")
        String prefix();
    }
}
