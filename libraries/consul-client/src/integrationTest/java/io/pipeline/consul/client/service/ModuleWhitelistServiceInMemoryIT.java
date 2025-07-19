package io.pipeline.consul.client.service;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.HashMap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ModuleWhitelistService using in-memory implementations.
 * This test validates whitelist functionality without requiring Consul.
 * 
 * Uses @QuarkusIntegrationTest to test against the packaged JAR.
 */
@QuarkusIntegrationTest
@TestProfile(ModuleWhitelistServiceInMemoryIT.InMemoryTestProfile.class)
class ModuleWhitelistServiceInMemoryIT {

    private static final Logger LOG = Logger.getLogger(ModuleWhitelistServiceInMemoryIT.class);
    
    private String testCluster;

    @BeforeEach
    void setUp() {
        // Create unique test cluster for isolation
        testCluster = "whitelist-test-" + System.currentTimeMillis();
        
        // Create cluster via REST API
        given()
            .when()
            .post("/api/v1/clusters/{clusterName}", testCluster)
            .then()
            .statusCode(201);
    }

    @Test
    void testWhitelistLifecycle() {
        String grpcServiceName = "test-module";
        
        // Create whitelist request
        Map<String, String> request = Map.of(
            "implementationName", "Test Service",
            "grpcServiceName", grpcServiceName
        );
        
        // Add module to whitelist
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/v1/clusters/{cluster}/whitelist", testCluster)
            .then()
            .statusCode(200)
            .body("success", is(true));
        
        // Verify it's in the whitelist
        given()
            .when()
            .get("/api/v1/clusters/{cluster}/whitelist", testCluster)
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .body("find { it.implementationId == '" + grpcServiceName + "' }", notNullValue());
        
        // Try to add duplicate (should succeed but indicate it's already whitelisted)
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/v1/clusters/{cluster}/whitelist", testCluster)
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", containsString("already whitelisted"));
        
        // Try to remove from whitelist - test-module has special handling preventing removal
        given()
            .when()
            .delete("/api/v1/clusters/{cluster}/whitelist/{serviceId}", testCluster, grpcServiceName)
            .then()
            .statusCode(200)
            .body("success", is(false))
            .body("message", anyOf(
                containsString("Cannot remove module"),
                containsString("in use")
            ));
        
        // Verify it's still there since removal failed
        given()
            .when()
            .get("/api/v1/clusters/{cluster}/whitelist", testCluster)
            .then()
            .statusCode(200)
            .body("find { it.implementationId == '" + grpcServiceName + "' }", notNullValue());
    }

    @Test
    void testEmptyWhitelist() {
        // New cluster should have empty whitelist
        given()
            .when()
            .get("/api/v1/clusters/{cluster}/whitelist", testCluster)
            .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    @Test
    void testInvalidInputs() {
        // Test null cluster name - should fail
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("implementationName", "Test Service", "grpcServiceName", "test-module"))
            .when()
            .post("/api/v1/clusters/{cluster}/whitelist", (String) null)
            .then()
            .statusCode(404); // Not found due to null path parameter
        
        // Test empty cluster name - should fail
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("implementationName", "Test Service", "grpcServiceName", "test-module"))
            .when()
            .post("/api/v1/clusters/{cluster}/whitelist", "")
            .then()
            .statusCode(anyOf(is(400), is(404))); // Bad request or not found
        
        // Test invalid request body - should fail
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("invalid", "request"))
            .when()
            .post("/api/v1/clusters/{cluster}/whitelist", testCluster)
            .then()
            .statusCode(anyOf(is(400), is(500))); // Bad request or server error
    }

    @Test
    void testModuleRegistryIntegration() {
        // Test that in-memory module registry has basic modules
        given()
            .when()
            .get("/api/v1/modules")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .body("find { it.moduleName == 'tika' }", notNullValue())
            .body("find { it.moduleName == 'vectorizer' }", notNullValue())
            .body("find { it.moduleName == 'search' }", notNullValue());
    }

    public static class InMemoryTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new java.util.TreeMap<>();
            
            // Use in-memory registry for testing (no service discovery)
            config.put("pipeline.module-registry.type", "memory");
            
            // Configure basic modules
            config.put("pipeline.module-registry.basic-modules.enabled", "true");
            config.put("pipeline.module-registry.basic-modules.list", "tika,vectorizer,search");
            config.put("pipeline.module-registry.basic-modules.default-host", "localhost");
            config.put("pipeline.module-registry.basic-modules.base-port", "8080");
            config.put("pipeline.module-registry.basic-modules.default-version", "1.0.0");
            config.put("pipeline.module-registry.basic-modules.service-type", "GRPC");
            
            // Keep Consul for KV operations but disable service discovery
            config.put("quarkus.consul-config.enabled", "false");
            // Note: We still need Consul KV for whitelist operations
            
            // Basic pipeline config
            config.put("pipeline.consul.kv-prefix", "test-pipeline");
            
            // Disable config validation
            config.put("quarkus.configuration.build-time-mismatch-at-runtime", "warn");
            config.put("smallrye.config.validate", "false");
            
            return config;
        }
    }
}