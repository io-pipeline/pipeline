package io.pipeline.consul.client.service;

import io.pipeline.consul.client.integration.InMemoryRegistryTestProfile;
import io.pipeline.api.service.ModuleRegistryService;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.http.ContentType;
import io.restassured.common.mapper.TypeRef;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.jboss.logging.Logger;

import static io.restassured.RestAssured.given;

/**
 * Integration tests for ModuleRegistryService using in-memory framework.
 * Uses @QuarkusIntegrationTest to test against the packaged JAR.
 */
@QuarkusIntegrationTest
@TestProfile(InMemoryRegistryTestProfile.class)
class GlobalModuleRegistryServiceIT extends ModuleRegistryServiceTestBase {
    
    private static final Logger LOG = Logger.getLogger(GlobalModuleRegistryServiceIT.class);
    private final List<String> createdClusters = new ArrayList<>();
    
    @Override
    @BeforeEach
    void setupDependencies() {
        // For integration tests, we use REST API calls with RestAssured
        // RestAssured automatically uses the correct test port
        
        // Create a REST-based adapter for the ModuleRegistryService
        this.globalModuleRegistryService = new RestBasedModuleRegistryService();
    }

    @AfterEach
    void cleanup() {
        for (String clusterName : createdClusters) {
            LOG.infof("Cleaning up cluster: %s", clusterName);
            try {
                given().when().delete("/api/v1/clusters/{clusterName}", clusterName).then().statusCode(200);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to delete cluster %s during cleanup", clusterName);
            }
        }
        createdClusters.clear();
    }
    
    /**
     * REST-based implementation of ModuleRegistryService for integration testing
     */
    private static class RestBasedModuleRegistryService implements ModuleRegistryService {
        private static final Logger LOG = Logger.getLogger(RestBasedModuleRegistryService.class);
        
        public RestBasedModuleRegistryService() {
        }
        
        @Override
        public Uni<ModuleRegistration> registerModule(String moduleName, String implementationId, 
                String host, int port, String serviceType, String version, 
                Map<String, String> metadata, String engineHost, int enginePort, String jsonSchema) {
            
            // Use a map to build the request object
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
            
            LOG.debugf("Sending registration request for module: %s", moduleName);
            
            return Uni.createFrom().item(() -> {
                Response response = RestAssured
                    .given()
                        .log().all()
                        .contentType(ContentType.JSON)
                        .body(requestMap)
                    .when()
                        .post("/api/v1/modules/register")
                    .then()
                        .log().all()
                        .extract().response();
                
                if (response.getStatusCode() == 201) {
                    return response.as(ModuleRegistration.class);
                } else if (response.getStatusCode() == 409) {
                    throw new RuntimeException("Module already exists at endpoint " + host + ":" + port);
                } else {
                    String errorBody = response.getBody().asString();
                    LOG.errorf("Failed to register module. Status: %d, Error: %s", response.getStatusCode(), errorBody);
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
            // Not implemented for REST-based testing
            return Uni.createFrom().item(new ZombieCleanupResult(0, 0, List.of()));
        }
        
        @Override
        public Uni<Boolean> archiveService(String serviceName, String reason) {
            // Not implemented for REST-based testing
            return Uni.createFrom().item(false);
        }
        
        @Override
        public Uni<Integer> cleanupStaleWhitelistedModules() {
            // Not implemented for REST-based testing
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
                // For REST-based testing, we can check if we can get the module
                // This implementation assumes the service name is the module ID
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