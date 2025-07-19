package io.pipeline.consul.client.api;

import io.pipeline.consul.client.integration.InMemoryRegistryTestProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Simple integration test for REST endpoints using in-memory registry.
 * Tests that the REST endpoints are properly exposed and functional
 * with the in-memory module registry for faster testing.
 */
@QuarkusIntegrationTest
@TestProfile(InMemoryRegistryTestProfile.class)
public class RestEndpointIT {
    
    private static final Logger LOG = Logger.getLogger(RestEndpointIT.class);
    
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
    void testHealthEndpoint() {
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
    
    @Test
    void testOpenApiEndpoint() {
        given()
            .when().get("/q/openapi")
            .then()
                .statusCode(200)
                .contentType("application/yaml");
    }
    
    @Test
    void testClusterEndpointWorks() {
        // Test that cluster endpoint works properly with in-memory registry
        String clusterName = "endpoint-test-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing cluster endpoint functionality with: %s", clusterName);
        
        // Test cluster listing works
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(200);
                
        // Create a cluster to test endpoint functionality
        given()
            .contentType("application/json")
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
            
        // Verify it shows up in listing
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(200)
                .body("name", hasItem(clusterName));
    }
    
    @Test
    void testPipelineEndpointWorks() {
        // Test that pipeline endpoint works with existing cluster
        String clusterName = "pipeline-endpoint-test-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing pipeline endpoint functionality with cluster: %s", clusterName);
        
        // First create a cluster
        given()
            .contentType("application/json")
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
            
        // Test pipeline listing endpoint works
        given()
            .when().get("/api/v1/clusters/{clusterName}/pipelines", clusterName)
            .then()
                .statusCode(200);
    }
}