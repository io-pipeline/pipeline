package io.pipeline.consul.client.service.registry;

import io.pipeline.api.service.ModuleRegistryService;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Abstract base class for ModuleRegistryService implementations.
 * Contains common business logic that is shared between different storage backends.
 */
public abstract class AbstractModuleRegistryService implements ModuleRegistryService {

    private static final Logger LOG = Logger.getLogger(AbstractModuleRegistryService.class);

    /**
     * Generate a unique module ID for registration
     */
    protected String generateModuleId(String moduleName, String implementationId) {
        return moduleName;
    }

    /**
     * Validate module registration parameters
     */
    protected void validateRegistrationParameters(String moduleName, String implementationId, 
                                                String host, int port, String serviceType) {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name cannot be null or empty");
        }
        if (implementationId == null || implementationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Implementation ID cannot be null or empty");
        }
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (serviceType == null || serviceType.trim().isEmpty()) {
            throw new IllegalArgumentException("Service type cannot be null or empty");
        }
    }

    /**
     * Create a module registration record with common fields
     */
    protected ModuleRegistration createModuleRegistration(String moduleId, String moduleName, 
                                                        String implementationId, String host, int port, 
                                                        String serviceType, String version, 
                                                        Map<String, String> metadata, String engineHost, 
                                                        int enginePort, String jsonSchema) {
        return new ModuleRegistration(
            moduleId,
            moduleName,
            implementationId,
            host,
            port,
            serviceType,
            version != null ? version : "1.0.0",
            metadata,
            System.currentTimeMillis(),
            engineHost,
            enginePort,
            jsonSchema,
            true, // enabled by default
            null, // containerId - to be filled by implementations
            null, // containerName - to be filled by implementations
            null, // hostname - to be filled by implementations
            ModuleStatus.REGISTERING
        );
    }

    /**
     * Log registration attempt
     */
    protected void logRegistrationAttempt(String moduleName, String implementationId, String host, int port) {
        LOG.infof("Attempting to register module: %s [%s] at %s:%d", moduleName, implementationId, host, port);
    }

    /**
     * Log registration success
     */
    protected void logRegistrationSuccess(String moduleId, String moduleName) {
        LOG.infof("Successfully registered module: %s with ID: %s", moduleName, moduleId);
    }

    /**
     * Log registration failure
     */
    protected void logRegistrationFailure(String moduleName, String implementationId, Throwable error) {
        LOG.errorf(error, "Failed to register module: %s [%s]", moduleName, implementationId);
    }

    /**
     * Log deregistration
     */
    protected void logDeregistration(String moduleId) {
        LOG.infof("Deregistering module: %s", moduleId);
    }

    /**
     * Common validation for module operations
     */
    protected void validateModuleId(String moduleId) {
        if (moduleId == null || moduleId.trim().isEmpty()) {
            throw new IllegalArgumentException("Module ID cannot be null or empty");
        }
    }

    /**
     * Filter enabled modules from a set
     */
    protected Set<ModuleRegistration> filterEnabledModules(Set<ModuleRegistration> modules) {
        return modules.stream()
                .filter(ModuleRegistration::enabled)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Create a zombie cleanup result
     */
    protected ZombieCleanupResult createCleanupResult(int detected, int cleaned, java.util.List<String> errors) {
        return new ZombieCleanupResult(detected, cleaned, errors);
    }

    // Abstract methods that must be implemented by concrete classes

    /**
     * Store module registration data
     */
    protected abstract Uni<ModuleRegistration> storeModuleRegistration(ModuleRegistration registration);

    /**
     * Retrieve all stored module registrations
     */
    protected abstract Uni<Set<ModuleRegistration>> retrieveStoredModules();

    /**
     * Retrieve a specific module by ID
     */
    protected abstract Uni<ModuleRegistration> retrieveModuleById(String moduleId);

    /**
     * Update module registration data
     */
    protected abstract Uni<ModuleRegistration> updateStoredModule(ModuleRegistration registration);

    /**
     * Remove module registration data
     */
    protected abstract Uni<Void> removeStoredModule(String moduleId);

    /**
     * Perform health check for a module
     */
    protected abstract Uni<ServiceHealthStatus> performHealthCheck(String moduleId);

    /**
     * Get health status for a module
     */
    protected abstract Uni<HealthStatus> getModuleHealth(String moduleId);
}