package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.consul.config.ConsulConfigSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;

/**
 * Provides health check configuration values.
 * This abstraction makes it easier to test services that need health check config.
 */
@ApplicationScoped
public class HealthCheckConfigProvider {

    @Inject
    ConsulConfigSource config;

    /**
     * Default constructor for CDI.
     */
    public HealthCheckConfigProvider() {
        // Default constructor for CDI
    }

    /**
     * Gets the configured health check interval.
     * 
     * @return The duration between health checks
     */
    public Duration getCheckInterval() {
        return config.consul().health().checkInterval();
    }

    /**
     * Gets the configured time after which to deregister failed services.
     * 
     * @return The duration after which to deregister failed services
     */
    public Duration getDeregisterAfter() {
        return config.consul().health().deregisterAfter();
    }

    /**
     * Checks if automatic cleanup of zombie instances is enabled.
     * 
     * @return True if automatic cleanup is enabled, false otherwise
     */
    public boolean isCleanupEnabled() {
        return config.consul().cleanup().enabled();
    }
}
