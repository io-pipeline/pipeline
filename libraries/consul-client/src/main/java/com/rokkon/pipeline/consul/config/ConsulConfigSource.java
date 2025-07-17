package com.rokkon.pipeline.consul.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration properties for Consul-based settings.
 * These are simple properties that can be managed through consul-config.
 * Complex data like pipelines and module registrations remain in KV store.
 */
@ConfigMapping(prefix = "pipeline", namingStrategy = NamingStrategy.KEBAB_CASE)
public interface ConsulConfigSource {

    /**
     * Engine configuration
     * 
     * @return The engine configuration settings
     */
    @WithName("engine")
    EngineConfig engine();

    /**
     * Consul-specific configuration
     * 
     * @return The Consul-specific configuration settings
     */
    @WithName("consul")
    ConsulConfig consul();

    /**
     * Module management configuration
     * 
     * @return The module management configuration settings
     */
    @WithName("modules")
    ModulesConfig modules();

    /**
     * Default cluster configuration
     * 
     * @return The default cluster configuration settings
     */
    @WithName("default-cluster")
    DefaultClusterConfig defaultCluster();

    /**
     * Configuration for the Rokkon engine.
     * Contains settings for ports, instance identification, and debug mode.
     */
    interface EngineConfig {
        /**
         * gRPC server port
         * 
         * @return The port number for the gRPC server
         */
        @WithDefault("49000")
        int grpcPort();

        /**
         * REST API port
         * 
         * @return The port number for the REST API
         */
        @WithDefault("8080")
        int restPort();

        /**
         * Engine instance ID (auto-generated if not set)
         * 
         * @return An Optional containing the engine instance ID, or empty if not set
         */
        Optional<String> instanceId();

        /**
         * Enable debug logging
         * 
         * @return True if debug logging is enabled, false otherwise
         */
        @WithDefault("false")
        boolean debug();
    }

    /**
     * Configuration for Consul service interactions.
     * Contains settings for cleanup and health check operations.
     */
    interface ConsulConfig {
        /**
         * Cleanup configuration
         * 
         * @return The cleanup configuration settings
         */
        @WithName("cleanup")
        CleanupConfig cleanup();

        /**
         * Health check configuration
         * 
         * @return The health check configuration settings
         */
        @WithName("health")
        HealthConfig health();

        /**
         * Configuration for automatic cleanup operations.
         * Contains settings for zombie instance detection and cleanup.
         */
        interface CleanupConfig {
            /**
             * Enable automatic zombie instance cleanup
             * 
             * @return True if automatic zombie instance cleanup is enabled, false otherwise
             */
            @WithDefault("true")
            boolean enabled();

            /**
             * Interval between cleanup runs
             * 
             * @return The duration between cleanup runs
             */
            @WithDefault("5m")
            Duration interval();

            /**
             * How long to wait before considering an unhealthy instance a zombie
             * 
             * @return The duration threshold for considering an instance a zombie
             */
            @WithDefault("2m")
            Duration zombieThreshold();

            /**
             * Enable cleanup of stale whitelist entries
             * 
             * @return True if cleanup of stale whitelist entries is enabled, false otherwise
             */
            @WithDefault("true")
            boolean cleanupStaleWhitelist();
        }

        /**
         * Configuration for health check operations.
         * Contains settings for health check intervals and timeouts.
         */
        interface HealthConfig {
            /**
             * Health check interval for registered modules
             * 
             * @return The duration between health checks
             */
            @WithDefault("10s")
            Duration checkInterval();

            /**
             * Time after which to deregister failed services
             * 
             * @return The duration after which to deregister failed services
             */
            @WithDefault("1m")
            Duration deregisterAfter();

            /**
             * Timeout for health check connections
             * 
             * @return The duration to wait before timing out a health check connection
             */
            @WithDefault("5s")
            Duration timeout();
        }
    }

    /**
     * Configuration for module management.
     * Contains settings for module discovery, whitelisting, and connection parameters.
     */
    interface ModulesConfig {
        /**
         * Enable automatic module discovery from Consul services
         * 
         * @return True if automatic module discovery is enabled, false otherwise
         */
        @WithDefault("false")
        boolean autoDiscover();

        /**
         * Service name prefix for auto-discovery
         * 
         * @return The prefix used for service names during auto-discovery
         */
        @WithDefault("module-")
        String servicePrefix();

        /**
         * Require modules to be explicitly whitelisted before use
         * 
         * @return True if modules must be explicitly whitelisted, false otherwise
         */
        @WithDefault("true")
        boolean requireWhitelist();

        /**
         * Default module connection timeout
         * 
         * @return The duration to wait before timing out a module connection
         */
        @WithDefault("30s")
        Duration connectionTimeout();

        /**
         * Maximum number of instances per module type
         * 
         * @return The maximum number of instances allowed per module type
         */
        @WithDefault("10")
        int maxInstancesPerModule();
    }

    /**
     * Configuration for the default cluster.
     * Contains settings for cluster creation and identification.
     */
    interface DefaultClusterConfig {
        /**
         * Name of the default cluster
         * 
         * @return The name of the default cluster
         */
        @WithDefault("default")
        String name();

        /**
         * Auto-create default cluster on startup
         * 
         * @return True if the default cluster should be auto-created on startup, false otherwise
         */
        @WithDefault("true")
        boolean autoCreate();

        /**
         * Description for auto-created default cluster
         * 
         * @return The description for the auto-created default cluster
         */
        @WithDefault("Default cluster for Rokkon pipelines")
        String description();
    }
}
