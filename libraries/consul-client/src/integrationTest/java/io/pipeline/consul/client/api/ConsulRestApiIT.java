package io.pipeline.consul.client.api;

import io.pipeline.consul.client.integration.InMemoryRegistryTestProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for the Consul REST APIs using in-memory registry.
 * This test verifies that the REST endpoints work correctly with Consul KV 
 * but uses in-memory module registry for faster testing.
 */
@QuarkusIntegrationTest
@TestProfile(InMemoryRegistryTestProfile.class)
class ConsulRestApiIT {
    
    private static final Logger LOG = Logger.getLogger(ConsulRestApiIT.class);
    
    // Track clusters created during tests for cleanup
    private final List<String> createdClusters = new ArrayList<>();
    
    @BeforeEach
    void setup() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
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
    void testClusterCrudOperations() {
        String clusterName = "test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing cluster CRUD operations with: %s", clusterName);
        
        // Create cluster
        given()
            .contentType("application/json")
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201)
            .body("valid", is(true));
            
        // List clusters - note: response format is array of objects with 'name' field
        given()
            .when()
            .get("/api/v1/clusters")
            .then()
            .statusCode(200)
            .body("name", hasItem(clusterName));
            
        // Get specific cluster
        given()
            .when()
            .get("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(200);
            
        // Delete cluster (cleanup will also try to delete, but that's okay)
        given()
            .when()
            .delete("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(200)
            .body("valid", is(true));
    }
    
    @Test
    void testClusterBasedRestOperations() {
        String clusterName = "rest-test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing cluster-based REST operations with: %s", clusterName);
        
        // Create cluster
        given()
            .contentType("application/json")
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
            
        // Verify cluster exists and can be retrieved
        given()
            .when()
            .get("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(200);
            
        // Verify cluster shows up in listing
        given()
            .when()
            .get("/api/v1/clusters")
            .then()
            .statusCode(200)
            .body("name", hasItem(clusterName));
            
        // Test pipeline listing endpoint (should return empty for new cluster)
        given()
            .when()
            .get("/api/v1/clusters/{clusterName}/pipelines", clusterName)
            .then()
            .statusCode(200);
    }
    
    @Test
    void testHealthCheck() {
        given()
            .when()
            .get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
}