package io.pipeline.consul.client.service.registry.config;

import io.pipeline.api.service.ModuleRegistryService;
import io.pipeline.consul.client.service.registry.consul.ConsulModuleRegistryService;
import io.pipeline.consul.client.service.registry.memory.InMemoryModuleRegistryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Producer for ModuleRegistryService implementations based on configuration
 */
@ApplicationScoped
public class ModuleRegistryProducer {

    private static final Logger LOG = Logger.getLogger(ModuleRegistryProducer.class);

    @Inject
    ModuleRegistryConfig config;

    @Inject
    @ConsulRegistry
    ConsulModuleRegistryService consulImpl;

    @Inject
    @InMemoryRegistry
    InMemoryModuleRegistryService memoryImpl;

    @Produces
    @Singleton
    public ModuleRegistryService produceModuleRegistryService() {
        String registryType = config.type().toLowerCase();
        
        LOG.infof("Creating ModuleRegistryService of type: %s", registryType);
        
        return switch (registryType) {
            case "memory" -> {
                LOG.info("Using InMemoryModuleRegistryService");
                yield memoryImpl;
            }
            case "consul" -> {
                LOG.info("Using ConsulModuleRegistryService");
                yield consulImpl;
            }
            default -> {
                LOG.warnf("Unknown registry type '%s', defaulting to consul", registryType);
                yield consulImpl;
            }
        };
    }
}