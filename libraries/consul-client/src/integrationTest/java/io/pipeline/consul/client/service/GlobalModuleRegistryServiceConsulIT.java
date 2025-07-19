package io.pipeline.consul.client.service;

import io.pipeline.consul.client.profile.WithConsulConfigProfile;
import io.pipeline.api.service.ModuleRegistryService;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.http.ContentType;
import io.restassured.common.mapper.TypeRef;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.time.Duration;
import org.jboss.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consul-specific integration tests for ModuleRegistryService.
 * These tests require full Consul functionality and are disabled by default.
 */
@QuarkusIntegrationTest
@TestProfile(WithConsulConfigProfile.class)
@Disabled("Requires full Consul setup - enable when needed for Consul-specific testing")
class GlobalModuleRegistryServiceConsulIT {
    
    private static final Logger LOG = Logger.getLogger(GlobalModuleRegistryServiceConsulIT.class);
    protected ModuleRegistryService globalModuleRegistryService;
    
    @BeforeEach
    void setupDependencies() {
        // Create a REST-based adapter for the ModuleRegistryService
        this.globalModuleRegistryService = new RestBasedModuleRegistryService();
    }
    
    @Test
    void testRegisterDuplicateModule() {
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
        
        // When - register the first module
        ModuleRegistryService.ModuleRegistration firstRegistration = globalModuleRegistryService.registerModule(
            moduleName, implementationId, host, port, serviceType, version, metadata, engineHost, enginePort, jsonSchema
        ).await().atMost(Duration.ofSeconds(10));
        
        // Then - first registration should succeed
        assertNotNull(firstRegistration);
        assertEquals(moduleName, firstRegistration.moduleName());
        assertEquals(implementationId, firstRegistration.moduleId());
        
        // When - try to register duplicate module with same endpoint
        Exception exception = assertThrows(RuntimeException.class, () -> {
            globalModuleRegistryService.registerModule(
                moduleName, implementationId, host, port, serviceType, version, metadata, engineHost, enginePort, jsonSchema
            ).await().atMost(Duration.ofSeconds(10));
        });
        
        // Then - should fail with appropriate error
        assertTrue(exception.getMessage().contains("already exists") || 
                  exception.getMessage().contains("409") ||
                  exception.getMessage().contains("conflict"));
    }

    @Test
    void testInvalidJsonSchema() {
        // Given
        String moduleName = "test-module-" + UUID.randomUUID().toString().substring(0, 8);
        String implementationId = "test-impl-1";
        String host = "localhost";
        int port = 8080;
        String serviceType = "MODULE";
        String version = "1.0.0";
        Map<String, String> metadata = Map.of("environment", "test");
        String engineHost = "engine-host";
        int enginePort = 9090;
        String invalidJsonSchema = "{ invalid json schema without proper structure";
        
        // When/Then - should either accept it (lenient) or fail gracefully
        try {
            ModuleRegistryService.ModuleRegistration registration = globalModuleRegistryService.registerModule(
                moduleName, implementationId, host, port, serviceType, version, metadata, engineHost, enginePort, invalidJsonSchema
            ).await().atMost(Duration.ofSeconds(10));
            
            // If it succeeds, verify basic properties
            assertNotNull(registration);
            assertEquals(moduleName, registration.moduleName());
        } catch (RuntimeException e) {
            // If it fails, should be due to invalid schema
            assertTrue(e.getMessage().contains("schema") || 
                      e.getMessage().contains("json") ||
                      e.getMessage().contains("400"));
        }
    }

    @Test  
    void testRegisterWithContainerMetadata() {
        // Given
        String moduleName = "test-module-" + UUID.randomUUID().toString().substring(0, 8);
        String implementationId = "test-impl-1";
        String host = "localhost";
        int port = 8080;
        String serviceType = "MODULE";
        String version = "1.0.0";
        Map<String, String> containerMetadata = Map.of(
            "container.id", "abc123def456",
            "container.image", "myorg/mymodule:1.0.0",
            "container.platform", "docker",
            "deployment.namespace", "pipeline-modules",
            "deployment.cluster", "prod-east-1"
        );
        String engineHost = "engine-host";
        int enginePort = 9090;
        String jsonSchema = """
            {
                "$schema": "http://json-schema.org/draft-07/schema#",
                "type": "object",
                "properties": {
                    "containerConfig": {
                        "type": "object",
                        "properties": {
                            "image": { "type": "string" },
                            "env": { "type": "array" }
                        }
                    }
                }
            }
            """;
        
        // When
        ModuleRegistryService.ModuleRegistration registration = globalModuleRegistryService.registerModule(
            moduleName, implementationId, host, port, serviceType, version, containerMetadata, engineHost, enginePort, jsonSchema
        ).await().atMost(Duration.ofSeconds(10));
        
        // Then
        assertNotNull(registration);
        assertEquals(moduleName, registration.moduleName());
        assertEquals(implementationId, registration.moduleId());
        assertEquals(host, registration.host());
        assertEquals(port, registration.port());
        assertEquals(serviceType, registration.serviceType());
        assertEquals(version, registration.version());
        
        // Verify container metadata is preserved
        Map<String, String> retrievedMetadata = registration.metadata();
        assertNotNull(retrievedMetadata);
        assertEquals("abc123def456", retrievedMetadata.get("container.id"));
        assertEquals("myorg/mymodule:1.0.0", retrievedMetadata.get("container.image"));
        assertEquals("docker", retrievedMetadata.get("container.platform"));
        assertEquals("pipeline-modules", retrievedMetadata.get("deployment.namespace"));
        assertEquals("prod-east-1", retrievedMetadata.get("deployment.cluster"));
        
        // Verify we can retrieve the module
        ModuleRegistryService.ModuleRegistration retrieved = globalModuleRegistryService.getModule(implementationId)
            .await().atMost(Duration.ofSeconds(10));
        assertNotNull(retrieved);
        assertEquals(registration.moduleId(), retrieved.moduleId());
        assertEquals(registration.metadata(), retrieved.metadata());
    }
    
    /**
     * REST-based implementation of ModuleRegistryService for integration testing
     */
    private static class RestBasedModuleRegistryService implements ModuleRegistryService {
        private static final Logger LOG = Logger.getLogger(RestBasedModuleRegistryService.class);
        
        @Override
        public Uni<ModuleRegistration> registerModule(String moduleName, String implementationId, 
                String host, int port, String serviceType, String version, 
                Map<String, String> metadata, String engineHost, int enginePort, String jsonSchema) {
            
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("moduleName", moduleName);
            requestMap.put("implementationId", implementationId);
            requestMap.put("host", host);
            requestMap.put("port", port);
            requestMap.put("serviceType", serviceType);
            requestMap.put("version", version);
            requestMap.put("metadata", metadata != null ? metadata : new HashMap<>());
            requestMap.put("engineHost", engineHost);
            requestMap.put("enginePort", enginePort);
            requestMap.put("jsonSchema", jsonSchema != null ? jsonSchema : "");
            
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                        .body(requestMap)
                    .when()
                        .post("/api/v1/modules/register")
                    .then()
                        .extract().response();
                
                if (response.getStatusCode() == 201) {
                    return response.as(ModuleRegistration.class);
                } else if (response.getStatusCode() == 409) {
                    throw new RuntimeException("Module already exists at endpoint " + host + ":" + port);
                } else {
                    String errorBody = response.getBody().asString();
                    throw new RuntimeException("Failed to register module: " + response.getStatusCode() + " - " + errorBody);
                }
            });
        }
        
        @Override
        public Uni<Set<ModuleRegistration>> listRegisteredModules() {
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                    .when()
                        .get("/api/v1/modules")
                    .then()
                        .extract().response();
                
                if (response.getStatusCode() == 200) {
                    return new java.util.HashSet<>(response.as(new TypeRef<List<ModuleRegistration>>() {}));
                } else {
                    throw new RuntimeException("Failed to list modules: " + response.getStatusCode());
                }
            });
        }
        
        @Override
        public Uni<Set<ModuleRegistration>> listEnabledModules() {
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                    .when()
                        .get("/api/v1/modules/enabled")
                    .then()
                        .extract().response();
                
                if (response.getStatusCode() == 200) {
                    return new java.util.HashSet<>(response.as(new TypeRef<List<ModuleRegistration>>() {}));
                } else {
                    throw new RuntimeException("Failed to list enabled modules: " + response.getStatusCode());
                }
            });
        }
        
        @Override
        public Uni<ModuleRegistration> getModule(String moduleId) {
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                    .when()
                        .get("/api/v1/modules/" + moduleId)
                    .then()
                        .extract().response();
                
                if (response.getStatusCode() == 200) {
                    return response.as(ModuleRegistration.class);
                } else if (response.getStatusCode() == 404) {
                    return null;
                } else {
                    throw new RuntimeException("Failed to get module: " + response.getStatusCode());
                }
            });
        }
        
        @Override
        public Uni<Boolean> disableModule(String moduleId) {
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                        .body("{}")
                    .when()
                        .put("/api/v1/modules/" + moduleId + "/disable")
                    .then()
                        .extract().response();
                
                return response.getStatusCode() == 200;
            });
        }
        
        @Override
        public Uni<Boolean> enableModule(String moduleId) {
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                        .body("{}")
                    .when()
                        .put("/api/v1/modules/" + moduleId + "/enable")
                    .then()
                        .extract().response();
                
                return response.getStatusCode() == 200;
            });
        }
        
        @Override
        public Uni<Void> deregisterModule(String moduleId) {
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                    .when()
                        .delete("/api/v1/modules/" + moduleId)
                    .then()
                        .extract().response();
                
                if (response.getStatusCode() != 204) {
                    throw new RuntimeException("Failed to deregister module: " + response.getStatusCode());
                }
                return null;
            });
        }
        
        @Override
        public Uni<ServiceHealthStatus> getModuleHealthStatus(String moduleId) {
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                    .when()
                        .get("/api/v1/modules/" + moduleId + "/health")
                    .then()
                        .extract().response();
                
                if (response.getStatusCode() == 200) {
                    return response.as(ServiceHealthStatus.class);
                } else {
                    throw new RuntimeException("Failed to get health status: " + response.getStatusCode());
                }
            });
        }
        
        @Override
        public Uni<ZombieCleanupResult> cleanupZombieInstances() {
            return Uni.createFrom().item(new ZombieCleanupResult(0, 0, List.of()));
        }
        
        @Override
        public Uni<Boolean> archiveService(String serviceName, String reason) {
            return Uni.createFrom().item(false);
        }
        
        @Override
        public Uni<Integer> cleanupStaleWhitelistedModules() {
            return Uni.createFrom().item(0);
        }
        
        @Override
        public Uni<Boolean> updateModuleStatus(String moduleId, ModuleStatus newStatus) {
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .contentType(ContentType.JSON)
                        .body(Map.of("status", newStatus.name()))
                    .when()
                        .put("/api/v1/modules/{moduleId}/status", moduleId)
                    .then()
                        .extract().response();
                
                return response.getStatusCode() == 200;
            });
        }
        
        @Override
        public Uni<Boolean> moduleExists(String serviceName) {
            return Uni.createFrom().item(() -> {
                try {
                    Response response = RestAssured
                        .given()
                            .contentType(ContentType.JSON)
                        .when()
                            .get("/api/v1/modules/" + serviceName)
                        .then()
                            .extract().response();
                    
                    return response.getStatusCode() == 200;
                } catch (Exception e) {
                    LOG.debugf("Module exists check failed for %s: %s", serviceName, e.getMessage());
                    return false;
                }
            });
        }
    }
}