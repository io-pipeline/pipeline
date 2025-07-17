package io.pipeline.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a module registration in the global registry.
 */
public record ModuleRegistration(
    @JsonProperty("moduleId") String moduleId,
    @JsonProperty("host") String host,
    @JsonProperty("port") int port,
    @JsonProperty("health") Health health,
    @JsonProperty("registeredAt") Instant registeredAt,
    @JsonProperty("lastHealthCheck") Instant lastHealthCheck,
    @JsonProperty("metadata") Map<String, String> metadata
) {
    
    /**
     * Health status of a registered module.
     */
    public enum Health {
        HEALTHY,
        UNHEALTHY,
        CRITICAL
    }
}