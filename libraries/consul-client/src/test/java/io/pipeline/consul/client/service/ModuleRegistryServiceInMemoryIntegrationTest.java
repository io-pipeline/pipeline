package io.pipeline.consul.client.service;

import io.pipeline.api.service.ModuleRegistryService;
import io.pipeline.consul.client.service.registry.config.ModuleRegistryConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ModuleRegistryService using in-memory implementation.
 * This test validates the registry functionality without requiring Consul.
 */
@QuarkusTest
@TestProfile(ModuleRegistryServiceInMemoryIntegrationTest.InMemoryTestProfile.class)
class ModuleRegistryServiceInMemoryIntegrationTest {

    @Inject
    ModuleRegistryService moduleRegistryService;

    @Test
    void testBasicModulesArePreloaded() {
        var modules = moduleRegistryService.listRegisteredModules()
            .await().indefinitely();

        assertNotNull(modules);
        assertFalse(modules.isEmpty());
        
        // Check that basic modules are pre-registered
        var moduleNames = modules.stream()
            .map(module -> module.moduleName())
            .collect(java.util.stream.Collectors.toSet());
            
        assertTrue(moduleNames.contains("tika"));
        assertTrue(moduleNames.contains("vectorizer"));
        assertTrue(moduleNames.contains("search"));
    }

    @Test
    void testModuleRegistrationAndRetrieval() {
        String moduleName = "test-module";
        String implementationId = "test-impl";
        String host = "localhost";
        int port = 9090;
        String serviceType = "GRPC";
        String version = "1.0.0";

        // Register a new module
        var registration = moduleRegistryService.registerModule(
            moduleName, implementationId, host, port, 
            serviceType, version, null, host, port, null
        ).await().indefinitely();

        assertNotNull(registration);
        assertEquals(moduleName, registration.moduleName());
        assertEquals(implementationId, registration.implementationId());
        assertEquals(host, registration.host());
        assertEquals(port, registration.port());

        // Retrieve the module
        var retrieved = moduleRegistryService.getModule(registration.moduleId())
            .await().indefinitely();

        assertNotNull(retrieved);
        assertEquals(registration.moduleId(), retrieved.moduleId());
        assertEquals(moduleName, retrieved.moduleName());
    }

    @Test
    void testModuleEnableDisable() {
        String moduleName = "toggle-test-module";
        String implementationId = "toggle-impl";

        // Register a module
        var registration = moduleRegistryService.registerModule(
            moduleName, implementationId, "localhost", 8080, 
            "GRPC", "1.0.0", null, "localhost", 8080, null
        ).await().indefinitely();

        String moduleId = registration.moduleId();

        // Module should be enabled by default
        assertTrue(registration.enabled());

        // Disable the module
        var disabled = moduleRegistryService.disableModule(moduleId)
            .await().indefinitely();
        assertTrue(disabled);

        // Check module is disabled
        var disabledModule = moduleRegistryService.getModule(moduleId)
            .await().indefinitely();
        assertFalse(disabledModule.enabled());

        // Re-enable the module
        var enabled = moduleRegistryService.enableModule(moduleId)
            .await().indefinitely();
        assertTrue(enabled);

        // Check module is enabled
        var enabledModule = moduleRegistryService.getModule(moduleId)
            .await().indefinitely();
        assertTrue(enabledModule.enabled());
    }

    @Test
    void testListEnabledModules() {
        // Get initial enabled modules
        var initialEnabled = moduleRegistryService.listEnabledModules()
            .await().indefinitely();

        // Register and disable a module
        var registration = moduleRegistryService.registerModule(
            "disabled-test", "disabled-impl", "localhost", 7070,
            "GRPC", "1.0.0", null, "localhost", 7070, null
        ).await().indefinitely();

        moduleRegistryService.disableModule(registration.moduleId())
            .await().indefinitely();

        // List enabled modules should not include the disabled one
        var enabledModules = moduleRegistryService.listEnabledModules()
            .await().indefinitely();

        var enabledIds = enabledModules.stream()
            .map(module -> module.moduleId())
            .collect(java.util.stream.Collectors.toSet());

        assertFalse(enabledIds.contains(registration.moduleId()));
    }

    @Test
    void testModuleDeregistration() {
        String moduleName = "deregister-test";
        String implementationId = "deregister-impl";

        // Register a module
        var registration = moduleRegistryService.registerModule(
            moduleName, implementationId, "localhost", 6060,
            "GRPC", "1.0.0", null, "localhost", 6060, null
        ).await().indefinitely();

        String moduleId = registration.moduleId();

        // Verify module exists
        var retrievedBefore = moduleRegistryService.getModule(moduleId)
            .await().indefinitely();
        assertNotNull(retrievedBefore);

        // Deregister the module
        moduleRegistryService.deregisterModule(moduleId)
            .await().indefinitely();

        // Verify module no longer exists
        var retrievedAfter = moduleRegistryService.getModule(moduleId)
            .await().indefinitely();
        assertNull(retrievedAfter);
    }

    public static class InMemoryTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                // Use in-memory registry for testing
                "pipeline.module-registry.type", "memory",
                
                // Configure basic modules
                "pipeline.module-registry.basic-modules.enabled", "true",
                "pipeline.module-registry.basic-modules.list", "tika,vectorizer,search",
                "pipeline.module-registry.basic-modules.default-host", "localhost",
                "pipeline.module-registry.basic-modules.base-port", "8080",
                "pipeline.module-registry.basic-modules.default-version", "1.0.0",
                "pipeline.module-registry.basic-modules.service-type", "GRPC"
            );
        }
    }
}