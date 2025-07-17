package io.pipeline.api.service;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service interface for managing global module registrations.
 * Modules are registered globally and can be referenced by clusters.
 */
public interface ModuleRegistryService {

    /**
     * Health status enum for module health checks
     */
    enum HealthStatus {
        PASSING,
        WARNING,
        CRITICAL;
    }

    /**
     * Module registration status enum
     */
    enum ModuleStatus {
        REGISTERING,  // Module is in the process of registering
        REGISTERED,   // Module is successfully registered and healthy
        ACTIVE,       // Alias for REGISTERED, module is active
        FAILED        // Module registration failed
    }

    /**
     * Module registration data stored globally
     */
    record ModuleRegistration(
        String moduleId,
        String moduleName,
        String implementationId,
        String host,
        int port,
        String serviceType,
        String version,
        Map<String, String> metadata,
        long registeredAt,
        String engineHost,
        int enginePort,
        String jsonSchema,  // Optional JSON schema for validation
        boolean enabled,    // Whether the module is enabled or disabled
        String containerId, // Docker container ID (if available)
        String containerName, // Docker container name (if available)
        String hostname,    // Container hostname (if available)
        ModuleStatus status // Registration status
    ) {}

    /**
     * Result of zombie cleanup operation
     */
    record ZombieCleanupResult(
        int zombiesDetected,
        int zombiesCleaned,
        List<String> errors
    ) {}

    /**
     * Service health status record
     */
    record ServiceHealthStatus(
        ModuleRegistration module,
        HealthStatus healthStatus,
        boolean exists
    ) {
        public boolean isZombie() {
            // Consider as zombie if:
            // 1. Service doesn't exist in Consul health checks
            // 2. Health status is critical (failing for extended period)
            return !exists || healthStatus == HealthStatus.CRITICAL;
        }
    }

    /**
     * Register a module globally.
     * This creates both a service entry and stores metadata in KV.
     */
    Uni<ModuleRegistration> registerModule(
            String moduleName,
            String implementationId,
            String host,
            int port,
            String serviceType,
            String version,
            Map<String, String> metadata,
            String engineHost,
            int enginePort,
            String jsonSchema);

    /**
     * List all globally registered modules as an ordered set (no duplicates)
     * This includes both enabled and disabled modules
     */
    Uni<Set<ModuleRegistration>> listRegisteredModules();

    /**
     * List only enabled modules
     */
    Uni<Set<ModuleRegistration>> listEnabledModules();

    /**
     * Get a specific module by ID
     */
    Uni<ModuleRegistration> getModule(String moduleId);

    /**
     * Disable a module (sets enabled=false)
     */
    Uni<Boolean> disableModule(String moduleId);

    /**
     * Enable a module (sets enabled=true)
     */
    Uni<Boolean> enableModule(String moduleId);

    /**
     * Deregister a module (hard delete - removes from registry completely)
     */
    Uni<Void> deregisterModule(String moduleId);

    /**
     * Archive a service by moving it from active to archive namespace in Consul
     */
    Uni<Boolean> archiveService(String serviceName, String reason);

    /**
     * Clean up zombie instances - modules that are failing health checks or no longer exist
     */
    Uni<ZombieCleanupResult> cleanupZombieInstances();

    /**
     * Public method to check module health using Consul health checks
     * @param moduleId The module ID to check
     * @return Health status of the module
     */
    Uni<ServiceHealthStatus> getModuleHealthStatus(String moduleId);

    /**
     * Clean up stale entries in the whitelist (modules registered but not in Consul)
     */
    Uni<Integer> cleanupStaleWhitelistedModules();

    /**
     * Update the status of a module in the KV store
     * @param moduleId The module ID to update
     * @param newStatus The new status to set
     * @return true if successful, false otherwise
     */
    Uni<Boolean> updateModuleStatus(String moduleId, ModuleStatus newStatus);
}
