package io.pipeline.test.support;

import io.pipeline.api.events.ServiceListUpdatedEvent;
import io.pipeline.api.service.ModuleRegistryService;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A test-only implementation of {@link ModuleRegistryService}.
 * <p>
 * This bean is intended to be used in test environments (@QuarkusTest, @QuarkusIntegrationTest)
 * to override the default production implementation (like ConsulModuleRegistryService).
 * It does not connect to any external service discovery. Instead, on application startup,
 * it fires a {@link ServiceListUpdatedEvent} with a base set of service names plus any
 * additional services configured via the {@code pipeline.test.additional-mock-services} property.
 * This allows validators and other components that depend on the list of registered
 * services to function correctly in an integration test environment without needing
 * a live Consul instance and real gRPC services.
 * <p>
 * <strong>Configuration:</strong>
 * <ul>
 *   <li>{@code pipeline.test.additional-mock-services} - Comma-separated list of additional 
 *       service names to include in the mock registry (e.g., "custom-chunker,custom-parser")</li>
 * </ul>
 * <p>
 * To use this, the production implementation of ModuleRegistryService should be
 * annotated with {@code @DefaultBean}, and tests should configure:
 * {@code quarkus.arc.selected-alternatives=io.pipeline.test.support.InMemoryTestModuleRegistry}
 */
@Alternative
@ApplicationScoped
public class InMemoryTestModuleRegistry implements ModuleRegistryService {

    private static final Logger LOG = Logger.getLogger(InMemoryTestModuleRegistry.class);

    @Inject
    Event<ServiceListUpdatedEvent> serviceListUpdatedEvent;

    @ConfigProperty(name = "pipeline.test.additional-mock-services", defaultValue = "")
    Optional<List<String>> additionalMockServices;

    /**
     * Observes the Quarkus startup event and fires the service list update.
     * 
     * <p>Combines a default set of test services with any additional services configured
     * via the {@code pipeline.test.additional-mock-services} property.</p>
     *
     * @param ev The startup event.
     */
    void onStart(@Observes StartupEvent ev) {
        // Base set of service names required by the StepReferenceValidatorTestBase
        // and other validation tests to pass.
        Set<String> baseTestServices = Set.of(
                "service", "step2", "target", "duplicate-name", "service-a", "service-b",
                "new-service", "parser-service", "chunker-service", "vectorizer-service",
                "sink-service", "gutenberg-connector", "opensearch-sink", "parser",
                "chunker", "embedder", "echo", "testing-harness", "proxy-module",
                "filesystem-crawler", "echo-module", "ab", "bean-step1", "bean-step2"
        );

        // Combine base services with any additional configured services
        Set<String> allTestServices = new HashSet<>(baseTestServices);
        if (additionalMockServices.isPresent()) {
            List<String> additionalServices = additionalMockServices.get();
            allTestServices.addAll(additionalServices);
            LOG.infof("Added %d additional mock services: %s", additionalServices.size(), additionalServices);
        }

        LOG.infof("FIRING FAKE ServiceListUpdatedEvent with %d test services for testing environment...", allTestServices.size());
        serviceListUpdatedEvent.fire(new ServiceListUpdatedEvent(allTestServices));
        LOG.info("...FAKE ServiceListUpdatedEvent fired.");
    }

    @Override
    public Uni<Boolean> moduleExists(String serviceName) {
        LOG.warn("InMemoryTestModuleRegistry.moduleExists() called, but it's a no-op in tests.");
        return Uni.createFrom().item(true);
    }

    @Override
    public Uni<ModuleRegistration> registerModule(
            String moduleName,
            String implementationId,
            String host,
            int port,
            String serviceType,
            String version,
            Map<String, String> metadata,
            String engineHost,
            int enginePort,
            String jsonSchema) {
        LOG.warn("InMemoryTestModuleRegistry.registerModule() called, but it's a no-op in tests.");
        return Uni.createFrom().item(new ModuleRegistration(
                implementationId,
                moduleName,
                implementationId,
                host,
                port,
                serviceType,
                version,
                metadata,
                System.currentTimeMillis(),
                engineHost,
                enginePort,
                jsonSchema,
                true,
                "containerId",
                "containerName",
                "hostname",
                ModuleStatus.REGISTERED
        ));
    }

    @Override
    public Uni<Set<ModuleRegistration>> listRegisteredModules() {
        LOG.warn("InMemoryTestModuleRegistry.listRegisteredModules() called, but it's a no-op in tests.");
        return Uni.createFrom().item(Collections.emptySet());
    }

    @Override
    public Uni<Set<ModuleRegistration>> listEnabledModules() {
        LOG.warn("InMemoryTestModuleRegistry.listEnabledModules() called, but it's a no-op in tests.");
        return Uni.createFrom().item(Collections.emptySet());
    }

    @Override
    public Uni<ModuleRegistration> getModule(String moduleId) {
        LOG.warn("InMemoryTestModuleRegistry.getModule() called, but it's a no-op in tests.");
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<Boolean> disableModule(String moduleId) {
        LOG.warn("InMemoryTestModuleRegistry.disableModule() called, but it's a no-op in tests.");
        return Uni.createFrom().item(true);
    }

    @Override
    public Uni<Boolean> enableModule(String moduleId) {
        LOG.warn("InMemoryTestModuleRegistry.enableModule() called, but it's a no-op in tests.");
        return Uni.createFrom().item(true);
    }

    @Override
    public Uni<Void> deregisterModule(String moduleId) {
        LOG.warn("InMemoryTestModuleRegistry.deregisterModule() called, but it's a no-op in tests.");
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Boolean> archiveService(String serviceName, String reason) {
        LOG.warn("InMemoryTestModuleRegistry.archiveService() called, but it's a no-op in tests.");
        return Uni.createFrom().item(true);
    }

    @Override
    public Uni<ZombieCleanupResult> cleanupZombieInstances() {
        LOG.warn("InMemoryTestModuleRegistry.cleanupZombieInstances() called, but it's a no-op in tests.");
        return Uni.createFrom().item(new ZombieCleanupResult(0, 0, Collections.emptyList()));
    }

    @Override
    public Uni<ServiceHealthStatus> getModuleHealthStatus(String moduleId) {
        LOG.warn("InMemoryTestModuleRegistry.getModuleHealthStatus() called, but it's a no-op in tests.");
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<Integer> cleanupStaleWhitelistedModules() {
        LOG.warn("InMemoryTestModuleRegistry.cleanupStaleWhitelistedModules() called, but it's a no-op in tests.");
        return Uni.createFrom().item(0);
    }

    @Override
    public Uni<Boolean> updateModuleStatus(String moduleId, ModuleStatus newStatus) {
        LOG.warn("InMemoryTestModuleRegistry.updateModuleStatus() called, but it's a no-op in tests.");
        return Uni.createFrom().item(true);
    }
}