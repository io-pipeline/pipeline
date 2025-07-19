package io.pipeline.consul.client.service.registry.memory;

import io.pipeline.consul.client.service.registry.AbstractModuleRegistryService;
import io.pipeline.consul.client.service.registry.config.ModuleRegistryConfig;
import io.pipeline.consul.client.service.registry.config.InMemoryRegistry;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

/**
 * In-memory implementation of ModuleRegistryService.
 * Used for testing and when Consul is not available.
 * Can be pre-configured with basic modules for development.
 */
@ApplicationScoped
@InMemoryRegistry
public class InMemoryModuleRegistryService extends AbstractModuleRegistryService {

    private static final Logger LOG = Logger.getLogger(InMemoryModuleRegistryService.class);

    @Inject
    InMemoryModuleStore store;

    @Inject
    ModuleRegistryConfig config;

    @PostConstruct
    void init() {
        LOG.info("Initializing InMemoryModuleRegistryService");
        
        if (config.basicModules().enabled()) {
            initializeBasicModules();
        }
    }

    /**
     * Initialize basic modules for development/testing
     */
    private void initializeBasicModules() {
        List<String> basicModules = config.basicModules().list();
        String defaultHost = config.basicModules().defaultHost();
        int basePort = config.basicModules().basePort();
        String defaultVersion = config.basicModules().defaultVersion();
        String serviceType = config.basicModules().serviceType();

        LOG.infof("Initializing %d basic modules: %s", basicModules.size(), basicModules);

        for (int i = 0; i < basicModules.size(); i++) {
            String moduleName = basicModules.get(i);
            String moduleId = generateModuleId(moduleName, "basic");
            int port = basePort + i;

            Map<String, String> metadata = new HashMap<>();
            metadata.put("type", "basic");
            metadata.put("auto-registered", "true");

            ModuleRegistration registration = createModuleRegistration(
                moduleId, moduleName, "basic", defaultHost, port, 
                serviceType, defaultVersion, metadata, defaultHost, port, null
            );

            // Update status to registered for basic modules
            registration = new ModuleRegistration(
                registration.moduleId(), registration.moduleName(), registration.implementationId(),
                registration.host(), registration.port(), registration.serviceType(),
                registration.version(), registration.metadata(), registration.registeredAt(),
                registration.engineHost(), registration.enginePort(), registration.jsonSchema(),
                registration.enabled(), registration.containerId(), registration.containerName(),
                registration.hostname(), ModuleStatus.REGISTERED
            );

            store.storeModule(registration);
            LOG.infof("Pre-registered basic module: %s at %s:%d", moduleName, defaultHost, port);
        }
    }

    @Override
    public Uni<ModuleRegistration> registerModule(String moduleName, String implementationId, 
                                                 String host, int port, String serviceType, 
                                                 String version, Map<String, String> metadata, 
                                                 String engineHost, int enginePort, String jsonSchema) {
        try {
            validateRegistrationParameters(moduleName, implementationId, host, port, serviceType);
            logRegistrationAttempt(moduleName, implementationId, host, port);

            String moduleId = generateModuleId(moduleName, implementationId);
            ModuleRegistration registration = createModuleRegistration(
                moduleId, moduleName, implementationId, host, port, 
                serviceType, version, metadata, engineHost, enginePort, jsonSchema
            );

            return storeModuleRegistration(registration)
                .invoke(stored -> logRegistrationSuccess(stored.moduleId(), stored.moduleName()));

        } catch (Exception e) {
            logRegistrationFailure(moduleName, implementationId, e);
            return Uni.createFrom().failure(e);
        }
    }

    @Override
    public Uni<Set<ModuleRegistration>> listRegisteredModules() {
        return retrieveStoredModules();
    }

    @Override
    public Uni<Set<ModuleRegistration>> listEnabledModules() {
        return retrieveStoredModules()
            .map(this::filterEnabledModules);
    }

    @Override
    public Uni<ModuleRegistration> getModule(String moduleId) {
        validateModuleId(moduleId);
        return retrieveModuleById(moduleId);
    }

    @Override
    public Uni<Boolean> disableModule(String moduleId) {
        validateModuleId(moduleId);
        return retrieveModuleById(moduleId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Module not found: " + moduleId))
            .onItem().transform(module -> new ModuleRegistration(
                module.moduleId(), module.moduleName(), module.implementationId(),
                module.host(), module.port(), module.serviceType(), module.version(),
                module.metadata(), module.registeredAt(), module.engineHost(),
                module.enginePort(), module.jsonSchema(), false, // disabled
                module.containerId(), module.containerName(), module.hostname(), module.status()
            ))
            .onItem().transformToUni(this::updateStoredModule)
            .map(updated -> !updated.enabled());
    }

    @Override
    public Uni<Boolean> enableModule(String moduleId) {
        validateModuleId(moduleId);
        return retrieveModuleById(moduleId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Module not found: " + moduleId))
            .onItem().transform(module -> new ModuleRegistration(
                module.moduleId(), module.moduleName(), module.implementationId(),
                module.host(), module.port(), module.serviceType(), module.version(),
                module.metadata(), module.registeredAt(), module.engineHost(),
                module.enginePort(), module.jsonSchema(), true, // enabled
                module.containerId(), module.containerName(), module.hostname(), module.status()
            ))
            .onItem().transformToUni(this::updateStoredModule)
            .map(ModuleRegistration::enabled);
    }

    @Override
    public Uni<Void> deregisterModule(String moduleId) {
        validateModuleId(moduleId);
        logDeregistration(moduleId);
        return removeStoredModule(moduleId);
    }

    @Override
    public Uni<Boolean> archiveService(String serviceName, String reason) {
        LOG.infof("Archiving service %s: %s", serviceName, reason);
        // In-memory implementation doesn't support archiving
        return Uni.createFrom().item(true);
    }

    @Override
    public Uni<ZombieCleanupResult> cleanupZombieInstances() {
        LOG.info("Cleaning up zombie instances (in-memory implementation)");
        // In-memory implementation doesn't have zombies
        return Uni.createFrom().item(createCleanupResult(0, 0, new ArrayList<>()));
    }

    @Override
    public Uni<ServiceHealthStatus> getModuleHealthStatus(String moduleId) {
        validateModuleId(moduleId);
        return performHealthCheck(moduleId);
    }

    @Override
    public Uni<Integer> cleanupStaleWhitelistedModules() {
        LOG.info("Cleaning up stale whitelisted modules (in-memory implementation)");
        // In-memory implementation doesn't have stale modules
        return Uni.createFrom().item(0);
    }

    @Override
    public Uni<Boolean> updateModuleStatus(String moduleId, ModuleStatus newStatus) {
        validateModuleId(moduleId);
        return retrieveModuleById(moduleId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Module not found: " + moduleId))
            .onItem().transform(module -> new ModuleRegistration(
                module.moduleId(), module.moduleName(), module.implementationId(),
                module.host(), module.port(), module.serviceType(), module.version(),
                module.metadata(), module.registeredAt(), module.engineHost(),
                module.enginePort(), module.jsonSchema(), module.enabled(),
                module.containerId(), module.containerName(), module.hostname(), newStatus
            ))
            .onItem().transformToUni(this::updateStoredModule)
            .map(updated -> updated.status() == newStatus);
    }

    // Abstract method implementations

    @Override
    protected Uni<ModuleRegistration> storeModuleRegistration(ModuleRegistration registration) {
        store.storeModule(registration);
        return Uni.createFrom().item(registration);
    }

    @Override
    protected Uni<Set<ModuleRegistration>> retrieveStoredModules() {
        return Uni.createFrom().item(store.getAllModules());
    }

    @Override
    protected Uni<ModuleRegistration> retrieveModuleById(String moduleId) {
        ModuleRegistration module = store.getModule(moduleId);
        return Uni.createFrom().item(module);
    }

    @Override
    protected Uni<ModuleRegistration> updateStoredModule(ModuleRegistration registration) {
        return Uni.createFrom().item(store.updateModule(registration));
    }

    @Override
    protected Uni<Void> removeStoredModule(String moduleId) {
        store.removeModule(moduleId);
        return Uni.createFrom().voidItem();
    }

    @Override
    protected Uni<ServiceHealthStatus> performHealthCheck(String moduleId) {
        ModuleRegistration module = store.getModule(moduleId);
        if (module == null) {
            return Uni.createFrom().item(new ServiceHealthStatus(null, HealthStatus.CRITICAL, false));
        }

        HealthStatus health = store.getHealthStatus(moduleId);
        return Uni.createFrom().item(new ServiceHealthStatus(module, health, true));
    }

    @Override
    public Uni<Boolean> moduleExists(String serviceName) {
        // Hardcoded list of supported services for testing
        Set<String> supportedServices = Set.of(
            "filesystem",    // connector
            "echo", 
            "test-harness", 
            "parser", 
            "chunker", 
            "embedder", 
            "open-search",   // sink
            "test-module"    // Keep existing test compatibility
        );
        
        boolean exists = supportedServices.contains(serviceName);
        LOG.debugf("Module exists check for '%s': %s", serviceName, exists);
        return Uni.createFrom().item(exists);
    }

    @Override
    protected Uni<HealthStatus> getModuleHealth(String moduleId) {
        return Uni.createFrom().item(store.getHealthStatus(moduleId));
    }
}