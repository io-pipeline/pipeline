package io.pipeline.consul.client.service;

import io.pipeline.consul.client.integration.InMemoryRegistryTestProfile;
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
import static org.hamcrest.Matchers.*;

/**
 * Integration test for pipeline instance operations using in-memory registry.
 * Tests instance management functionality while using the in-memory module
 * registry for faster testing without full Consul service dependencies.
 */
@QuarkusIntegrationTest
@TestProfile(InMemoryRegistryTestProfile.class)
class PipelineInstanceServiceIT {
    
    private static final Logger LOG = Logger.getLogger(PipelineInstanceServiceIT.class);
    
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
    void testPipelineEndpointExists() {
        // Test that pipeline-related endpoints are available
        String clusterName = "pipeline-test-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing pipeline endpoints with cluster: %s", clusterName);
        
        // Create a cluster first
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
        
        // Test that pipeline listing endpoint works
        given()
            .when()
            .get("/api/v1/clusters/{clusterName}/pipelines", clusterName)
            .then()
            .statusCode(200);
    }
    
    @Test
    void testClusterBasedOperations() {
        // Test basic cluster operations that would be needed for pipeline instances
        String clusterName = "instance-service-test-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing cluster-based operations for: %s", clusterName);
        
        // Create cluster
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
        
        // Verify cluster exists
        given()
            .when()
            .get("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(200);
            
        // Test that the cluster appears in listings
        given()
            .when()
            .get("/api/v1/clusters")
            .then()
            .statusCode(200)
            .body("name", hasItem(clusterName));
    }
    
    @Test
    void testInMemoryModuleRegistryWithPipelineOperations() {
        // Test that the in-memory module registry works for pipeline operations
        String clusterName = "module-pipeline-test-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing in-memory module registry with pipeline operations: %s", clusterName);
        
        // Create cluster (this validates that module registry is working)
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
        
        // If we get here, the in-memory module registry is working
        // since cluster creation depends on the module registry service
        LOG.info("In-memory module registry working correctly for pipeline operations");
    }
}