package io.pipeline.consul.client.kv;

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
import static org.hamcrest.Matchers.equalTo;

/**
 * Basic integration test to verify Consul KV store connectivity using in-memory registry.
 * This test exercises basic KV operations through REST endpoints while using the 
 * in-memory module registry for faster testing without Consul dependencies.
 */
@QuarkusIntegrationTest
@TestProfile(InMemoryRegistryTestProfile.class)
public class ConsulKVBasicIT {

    private static final Logger LOG = Logger.getLogger(ConsulKVBasicIT.class);
    
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
    void testConsulConnectivity() {
        // Test that the application started with Consul connection
        LOG.info("Testing basic connectivity via health endpoint");
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
    
    @Test
    void testListClusters() {
        // Test that we can list clusters (exercises ConsulClient KV operations)
        LOG.info("Testing cluster listing via REST API");
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(200);
    }
    
    @Test
    void testCreateAndRetrieveCluster() {
        // Test basic KV operations: create and retrieve cluster
        String clusterName = "test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing KV operations: creating cluster %s", clusterName);
        
        // Create cluster
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(201);
        
        // Retrieve cluster
        LOG.infof("Testing KV operations: retrieving cluster %s", clusterName);
        given()
        .when()
            .get("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(200);
    }
    
    @Test
    void testKVOperationsWithMultipleClusters() {
        // Test KV operations with multiple clusters
        String cluster1 = "test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        String cluster2 = "test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(cluster1);
        createdClusters.add(cluster2);
        
        LOG.infof("Testing multiple KV operations: creating clusters %s and %s", cluster1, cluster2);
        
        // Create first cluster
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", cluster1)
        .then()
            .statusCode(201);
            
        // Create second cluster  
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", cluster2)
        .then()
            .statusCode(201);
        
        // List clusters should include both
        LOG.info("Testing that cluster listing includes both created clusters");
        given()
        .when()
            .get("/api/v1/clusters")
        .then()
            .statusCode(200);
            // Note: We could add more specific assertions here to verify both clusters are returned
    }
}