package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ModuleRegistration;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ZombieCleanupResult;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ServiceHealthStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for GlobalModuleRegistryService implementations.
 * Contains test logic that can be shared between unit and integration tests.
 */
public abstract class GlobalModuleRegistryServiceTestBase {
    
    protected GlobalModuleRegistryService globalModuleRegistryService;
    
    @BeforeEach
    abstract void setupDependencies();
    
    @Test
    void testRegisterModule() {
        // Given
        String moduleName = "test-module-" + UUID.randomUUID().toString().substring(0, 8);
        String implementationId = "test-impl-1";
        String host = "localhost";
        int port = 8080;
        String serviceType = "MODULE";
        String version = "1.0.0";
        Map<String, String> metadata = Map.of(
            "environment", "test",
            "owner", "test-team"
        );
        String engineHost = "engine-host";
        int enginePort = 9090;
        String jsonSchema = """
            {
                "$schema": "http://json-schema.org/draft-07/schema#",
                "type": "object",
                "properties": {
                    "test": { "type": "string" }
                }
            }
            """;
        
        // When
        ModuleRegistration result = globalModuleRegistryService.registerModule(
            moduleName, implementationId, host, port, serviceType, 
            version, metadata, engineHost, enginePort, jsonSchema
        ).await().atMost(Duration.ofSeconds(5));
        
        // Then
        assertNotNull(result);
        assertEquals(moduleName, result.moduleName());
        assertEquals(implementationId, result.implementationId());
        assertEquals(host, result.host());
        assertEquals(port, result.port());
        assertEquals(serviceType, result.serviceType());
        assertEquals(version, result.version());
        assertEquals(metadata, result.metadata());
        assertEquals(engineHost, result.engineHost());
        assertEquals(enginePort, result.enginePort());
        assertEquals(jsonSchema, result.jsonSchema());
        assertTrue(result.enabled());
        assertNotNull(result.moduleId());
        assertTrue(result.registeredAt() > 0);
    }
    
    @Test
    void testRegisterDuplicateModule() {
        // Given
        String moduleName = "duplicate-module-" + UUID.randomUUID().toString().substring(0, 8);
        String host = "localhost";
        int port = 8081;
        
        // Register first module
        globalModuleRegistryService.registerModule(
            moduleName, "impl-1", host, port, "MODULE", 
            "1.0.0", null, "engine", 9090, null
        ).await().atMost(Duration.ofSeconds(5));
        
        // When/Then - Try to register duplicate at same endpoint
        assertThrows(Exception.class, () -> {
            globalModuleRegistryService.registerModule(
                moduleName + "-2", "impl-2", host, port, "MODULE", 
                "1.0.0", null, "engine", 9090, null
            ).await().atMost(Duration.ofSeconds(5));
        });
    }
    
    @Test
    void testListRegisteredModules() {
        // Given - Register multiple modules
        String prefix = "list-test-" + UUID.randomUUID().toString().substring(0, 8);
        int moduleCount = 3;
        
        for (int i = 0; i < moduleCount; i++) {
            globalModuleRegistryService.registerModule(
                prefix + "-module-" + i, "impl-" + i, "localhost", 
                8090 + i, "MODULE", "1.0.0", null, "engine", 9090, null
            ).await().atMost(Duration.ofSeconds(5));
        }
        
        // When
        Set<ModuleRegistration> modules = globalModuleRegistryService.listRegisteredModules()
            .await().atMost(Duration.ofSeconds(5));
        
        // Then
        assertNotNull(modules);
        assertTrue(modules.size() >= moduleCount);
        
        // Verify our modules are in the list
        long ourModules = modules.stream()
            .filter(m -> m.moduleName().startsWith(prefix))
            .count();
        assertEquals(moduleCount, ourModules);
    }
    
    @Test
    void testEnableDisableModule() {
        // Given
        String moduleName = "toggle-module-" + UUID.randomUUID().toString().substring(0, 8);
        ModuleRegistration module = globalModuleRegistryService.registerModule(
            moduleName, "impl-1", "localhost", 8095, "MODULE", 
            "1.0.0", null, "engine", 9090, null
        ).await().atMost(Duration.ofSeconds(5));
        
        assertTrue(module.enabled());
        
        // When - Disable
        Boolean disabled = globalModuleRegistryService.disableModule(module.moduleId())
            .await().atMost(Duration.ofSeconds(5));
        
        // Then
        assertTrue(disabled);
        
        // Verify it's disabled
        ModuleRegistration disabledModule = globalModuleRegistryService.getModule(module.moduleId())
            .await().atMost(Duration.ofSeconds(5));
        assertFalse(disabledModule.enabled());
        
        // When - Enable
        Boolean enabled = globalModuleRegistryService.enableModule(module.moduleId())
            .await().atMost(Duration.ofSeconds(5));
        
        // Then
        assertTrue(enabled);
        
        // Verify it's enabled
        ModuleRegistration enabledModule = globalModuleRegistryService.getModule(module.moduleId())
            .await().atMost(Duration.ofSeconds(5));
        assertTrue(enabledModule.enabled());
    }
    
    @Test
    void testListEnabledModules() {
        // Given
        String prefix = "enabled-test-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Register and disable one module
        ModuleRegistration module1 = globalModuleRegistryService.registerModule(
            prefix + "-disabled", "impl-1", "localhost", 8096, "MODULE", 
            "1.0.0", null, "engine", 9090, null
        ).await().atMost(Duration.ofSeconds(5));
        
        globalModuleRegistryService.disableModule(module1.moduleId())
            .await().atMost(Duration.ofSeconds(5));
        
        // Register enabled module
        globalModuleRegistryService.registerModule(
            prefix + "-enabled", "impl-2", "localhost", 8097, "MODULE", 
            "1.0.0", null, "engine", 9090, null
        ).await().atMost(Duration.ofSeconds(5));
        
        // When
        Set<ModuleRegistration> enabledModules = globalModuleRegistryService.listEnabledModules()
            .await().atMost(Duration.ofSeconds(5));
        
        // Then
        assertNotNull(enabledModules);
        
        // Should not contain disabled module
        boolean hasDisabled = enabledModules.stream()
            .anyMatch(m -> m.moduleName().equals(prefix + "-disabled"));
        assertFalse(hasDisabled);
        
        // Should contain enabled module
        boolean hasEnabled = enabledModules.stream()
            .anyMatch(m -> m.moduleName().equals(prefix + "-enabled"));
        assertTrue(hasEnabled);
    }
    
    @Test
    void testDeregisterModule() {
        // Given
        String moduleName = "deregister-module-" + UUID.randomUUID().toString().substring(0, 8);
        ModuleRegistration module = globalModuleRegistryService.registerModule(
            moduleName, "impl-1", "localhost", 8098, "MODULE", 
            "1.0.0", null, "engine", 9090, null
        ).await().atMost(Duration.ofSeconds(5));
        
        // When
        globalModuleRegistryService.deregisterModule(module.moduleId())
            .await().atMost(Duration.ofSeconds(5));
        
        // Then - Module should not exist
        ModuleRegistration deletedModule = globalModuleRegistryService.getModule(module.moduleId())
            .await().atMost(Duration.ofSeconds(5));
        assertNull(deletedModule);
        
        // And should not be in the list
        Set<ModuleRegistration> modules = globalModuleRegistryService.listRegisteredModules()
            .await().atMost(Duration.ofSeconds(5));
        
        boolean found = modules.stream()
            .anyMatch(m -> m.moduleId().equals(module.moduleId()));
        assertFalse(found);
    }
    
    @Test
    void testInvalidJsonSchema() {
        // Given
        String moduleName = "invalid-schema-" + UUID.randomUUID().toString().substring(0, 8);
        String invalidSchema = "{ invalid json schema }";
        
        // When/Then
        assertThrows(Exception.class, () -> {
            globalModuleRegistryService.registerModule(
                moduleName, "impl-1", "localhost", 8099, "MODULE", 
                "1.0.0", null, "engine", 9090, invalidSchema
            ).await().atMost(Duration.ofSeconds(5));
        });
    }
    
    @Test
    void testRegisterWithContainerMetadata() {
        // Given
        String moduleName = "container-module-" + UUID.randomUUID().toString().substring(0, 8);
        String containerId = "container-" + UUID.randomUUID().toString();
        Map<String, String> metadata = Map.of(
            "containerId", containerId,
            "containerName", "test-container",
            "hostname", "test-host"
        );
        
        // When
        ModuleRegistration result = globalModuleRegistryService.registerModule(
            moduleName, "impl-1", "localhost", 8100, "MODULE", 
            "1.0.0", metadata, "engine", 9090, null
        ).await().atMost(Duration.ofSeconds(5));
        
        // Then
        assertEquals(containerId, result.containerId());
        assertEquals("test-container", result.containerName());
        assertEquals("test-host", result.hostname());
    }
}