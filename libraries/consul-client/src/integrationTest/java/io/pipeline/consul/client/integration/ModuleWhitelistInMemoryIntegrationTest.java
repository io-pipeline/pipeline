package io.pipeline.consul.client.integration;

import io.pipeline.api.model.ModuleWhitelistRequest;
import io.pipeline.api.model.ModuleWhitelistResponse;
import io.pipeline.consul.client.api.ModuleWhitelistResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Integration test that verifies the ModuleWhitelistService works with the in-memory 
 * ModuleRegistryService implementation, demonstrating that the refactoring successfully
 * separated the business logic from the storage mechanism.
 */
@QuarkusIntegrationTest
@TestProfile(InMemoryRegistryTestProfile.class)
public class ModuleWhitelistInMemoryIntegrationTest {

    private static final Logger LOG = Logger.getLogger(ModuleWhitelistInMemoryIntegrationTest.class);
    
    // Track clusters created during tests for cleanup
    private final List<String> createdClusters = new ArrayList<>();
    
    @AfterEach
    public void cleanup() {
        // Clean up all clusters created during tests
        for (String clusterName : createdClusters) {
            LOG.infof("Cleaning up cluster: %s", clusterName);
            try {
                given()
                .when()
                    .delete("/api/v1/clusters/{clusterName}", clusterName)
                .then()
                    .statusCode(200);
                LOG.infof("Successfully deleted cluster: %s", clusterName);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to delete cluster %s during cleanup", clusterName);
            }
        }
        createdClusters.clear();
    }

    @Test
    public void testWhitelistModuleWithInMemoryRegistry() {
        String clusterName = "test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.info("Creating cluster: " + clusterName);
        
        // First create the cluster
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(201);
        
        // Now test whitelisting a module that exists in the in-memory registry
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "file-connector-v1",  // implementationName
            "filesystem",  // grpcServiceName - This should exist in the hardcoded list
            null,
            null
        );

        LOG.info("Testing module whitelist with in-memory registry - adding 'filesystem' module");

        // Test whitelisting the module
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/clusters/{clusterName}/whitelist", clusterName)
        .then()
            .log().all() // Log the response to see what's happening
            .statusCode(200)
            .body("success", is(true))
            .body("message", containsString("filesystem"));
    }

    @Test
    public void testWhitelistNonExistentModuleWithInMemoryRegistry() {
        String clusterName = "test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.info("Creating cluster: " + clusterName);
        
        // First create the cluster
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(201);
        
        // Test whitelisting a module that does NOT exist in the in-memory registry
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "some-implementation",  // implementationName
            "non-existent-module",  // grpcServiceName - This should NOT exist in the hardcoded list
            null,
            null
        );

        LOG.info("Testing module whitelist with in-memory registry - adding non-existent module (should fail)");

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/clusters/{clusterName}/whitelist", clusterName)
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("not found in registry"));
    }

    @Test
    public void testListWhitelistedModules() {
        String clusterName = "test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.info("Creating cluster: " + clusterName);
        
        // First create the cluster
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(201);
        
        // Then whitelist a module
        ModuleWhitelistRequest request = new ModuleWhitelistRequest(
            "echo-processor-v1",  // implementationName
            "echo",  // grpcServiceName - This should exist in the hardcoded list
            null,
            null
        );

        LOG.info("Adding echo module to whitelist");

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/clusters/{clusterName}/whitelist", clusterName)
        .then()
            .statusCode(200)
            .body("success", is(true));

        // Then list whitelisted modules
        LOG.info("Listing whitelisted modules");

        given()
        .when()
            .get("/api/v1/clusters/{clusterName}/whitelist", clusterName)
        .then()
            .log().all() // Log the response to see what's happening
            .statusCode(200);
    }
}