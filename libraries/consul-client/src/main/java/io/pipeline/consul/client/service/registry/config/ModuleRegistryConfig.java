package io.pipeline.consul.client.service.registry.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for module registry selection and basic modules
 */
@ConfigMapping(prefix = "pipeline.module-registry")
public interface ModuleRegistryConfig {

    /**
     * Type of module registry to use: "consul" or "memory"
     */
    @WithDefault("consul")
    String type();

    /**
     * Configuration for basic modules in in-memory mode
     */
    BasicModules basicModules();

    interface BasicModules {
        /**
         * Whether to enable pre-configured basic modules
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * List of basic module names to pre-configure
         */
        @WithDefault("tika,vectorizer,search")
        List<String> list();

        /**
         * Default host for basic modules
         */
        @WithDefault("localhost")
        String defaultHost();

        /**
         * Base port for basic modules (will increment for each module)
         */
        @WithDefault("9090")
        int basePort();

        /**
         * Default version for basic modules
         */
        @WithDefault("1.0.0")
        String defaultVersion();

        /**
         * Service type for basic modules
         */
        @WithDefault("grpc")
        String serviceType();
    }

    /**
     * Configuration for in-memory registry behavior
     */
    Optional<InMemoryConfig> inMemory();

    interface InMemoryConfig {
        /**
         * Whether to simulate health checks for in-memory modules
         */
        @WithDefault("true")
        boolean simulateHealthChecks();

        /**
         * Health check interval in seconds
         */
        @WithDefault("30")
        int healthCheckIntervalSeconds();

        /**
         * Whether to automatically mark modules as healthy
         */
        @WithDefault("true")
        boolean autoHealthy();
    }
}