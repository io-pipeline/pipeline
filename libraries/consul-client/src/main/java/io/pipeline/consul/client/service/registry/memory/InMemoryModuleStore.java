package io.pipeline.consul.client.service.registry.memory;

import io.pipeline.api.service.ModuleRegistryService.ModuleRegistration;
import io.pipeline.api.service.ModuleRegistryService.HealthStatus;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * In-memory storage for module registrations
 */
@ApplicationScoped
public class InMemoryModuleStore {

    private static final Logger LOG = Logger.getLogger(InMemoryModuleStore.class);

    private final ConcurrentMap<String, ModuleRegistration> modules = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, HealthStatus> healthStatus = new ConcurrentHashMap<>();

    /**
     * Store a module registration
     */
    public void storeModule(ModuleRegistration registration) {
        modules.put(registration.moduleId(), registration);
        // Default to healthy for in-memory modules
        healthStatus.put(registration.moduleId(), HealthStatus.PASSING);
        LOG.infof("Stored module: %s [%s]", registration.moduleName(), registration.moduleId());
    }

    /**
     * Retrieve a module by ID
     */
    public ModuleRegistration getModule(String moduleId) {
        return modules.get(moduleId);
    }

    /**
     * Retrieve all modules
     */
    public Set<ModuleRegistration> getAllModules() {
        return Set.copyOf(modules.values());
    }

    /**
     * Retrieve only enabled modules
     */
    public Set<ModuleRegistration> getEnabledModules() {
        return modules.values().stream()
                .filter(ModuleRegistration::enabled)
                .collect(Collectors.toSet());
    }

    /**
     * Update a module registration
     */
    public ModuleRegistration updateModule(ModuleRegistration registration) {
        modules.put(registration.moduleId(), registration);
        LOG.infof("Updated module: %s [%s]", registration.moduleName(), registration.moduleId());
        return registration;
    }

    /**
     * Remove a module registration
     */
    public void removeModule(String moduleId) {
        ModuleRegistration removed = modules.remove(moduleId);
        healthStatus.remove(moduleId);
        if (removed != null) {
            LOG.infof("Removed module: %s [%s]", removed.moduleName(), moduleId);
        }
    }

    /**
     * Get health status for a module
     */
    public HealthStatus getHealthStatus(String moduleId) {
        return healthStatus.getOrDefault(moduleId, HealthStatus.CRITICAL);
    }

    /**
     * Set health status for a module
     */
    public void setHealthStatus(String moduleId, HealthStatus status) {
        healthStatus.put(moduleId, status);
        LOG.debugf("Updated health status for module %s: %s", moduleId, status);
    }

    /**
     * Check if a module exists
     */
    public boolean exists(String moduleId) {
        return modules.containsKey(moduleId);
    }

    /**
     * Get number of registered modules
     */
    public int size() {
        return modules.size();
    }

    /**
     * Clear all modules (for testing)
     */
    public void clear() {
        modules.clear();
        healthStatus.clear();
        LOG.info("Cleared all modules from in-memory store");
    }

    /**
     * Get all module IDs
     */
    public Set<String> getAllModuleIds() {
        return Set.copyOf(modules.keySet());
    }
}