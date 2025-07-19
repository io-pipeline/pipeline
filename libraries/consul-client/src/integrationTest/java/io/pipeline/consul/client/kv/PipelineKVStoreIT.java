package io.pipeline.consul.client.kv;

import io.pipeline.consul.client.integration.InMemoryRegistryTestProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for pipeline KV store operations using in-memory registry.
 * Tests the KV store functionality through REST endpoints while using the 
 * in-memory module registry for faster testing without Consul dependencies.
 * Focuses on cluster operations and Consul KV storage verification.
 */
@QuarkusIntegrationTest
@TestProfile(InMemoryRegistryTestProfile.class)
public class PipelineKVStoreIT {
    
    private static final Logger LOG = Logger.getLogger(PipelineKVStoreIT.class);
    
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
    void testClusterKVOperations() throws Exception {
        String clusterName = "kv-test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing cluster KV operations with: %s", clusterName);
        
        // Create a cluster using the correct endpoint
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(201);
        
        // List clusters to verify creation
        LOG.info("Listing clusters to verify creation");
        given()
        .when()
            .get("/api/v1/clusters")
        .then()
            .statusCode(200)
            .body("name", hasItem(clusterName));
        
        // Verify individual cluster retrieval
        LOG.infof("Retrieving specific cluster: %s", clusterName);
        given()
        .when()
            .get("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(200);
            
        // Verify data is actually in Consul KV
        String consulHost = System.getProperty("pipeline.consul.host", "localhost");
        String consulPort = System.getProperty("pipeline.consul.port", "8500");
        
        LOG.infof("Verifying cluster data exists in Consul KV at %s:%s", consulHost, consulPort);
        
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        
        // Check if cluster data exists in Consul
        URI getUri = URI.create(String.format("http://%s:%s/v1/kv/test-pipeline/clusters/?keys", 
            consulHost, consulPort));
        HttpRequest getRequest = HttpRequest.newBuilder()
            .uri(getUri)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        HttpResponse<String> response = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Should be able to list cluster keys in Consul");
        
        String keys = response.body();
        assertTrue(keys.contains("test-pipeline/clusters/"), "Consul should contain cluster data");
        LOG.info("Successfully verified cluster data exists in Consul KV store");
    }
    
    @Test
    void testMultipleClusterKVOperations() {
        String cluster1 = "multi-kv-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        String cluster2 = "multi-kv-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(cluster1);
        createdClusters.add(cluster2);
        
        LOG.infof("Testing multiple cluster KV operations: %s, %s", cluster1, cluster2);
        
        // Create both clusters
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", cluster1)
        .then()
            .statusCode(201);
            
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", cluster2)
        .then()
            .statusCode(201);
        
        // Verify both clusters exist in listing
        LOG.info("Verifying both clusters appear in cluster listing");
        given()
        .when()
            .get("/api/v1/clusters")
        .then()
            .statusCode(200)
            .body("name", hasItem(cluster1))
            .body("name", hasItem(cluster2));
            
        // Test individual cluster retrieval for both
        LOG.infof("Testing individual cluster retrieval for: %s", cluster1);
        given()
        .when()
            .get("/api/v1/clusters/{clusterName}", cluster1)
        .then()
            .statusCode(200);
            
        given()
        .when()
            .get("/api/v1/clusters/{clusterName}", cluster2)
        .then()
            .statusCode(200);
    }
    
    @Test
    void testClusterKVStorageAndRetrieval() {
        String clusterName = "storage-test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing KV storage and retrieval with cluster: %s", clusterName);
        
        // Create cluster
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(201);
        
        // Verify cluster exists (tests KV retrieval)
        given()
        .when()
            .get("/api/v1/clusters/{clusterName}", clusterName)
        .then()
            .statusCode(200);
        
        // Test that non-existent cluster returns 404
        String nonExistentCluster = "non-existent-" + UUID.randomUUID().toString().substring(0, 8);
        LOG.infof("Testing non-existent cluster: %s", nonExistentCluster);
        given()
        .when()
            .get("/api/v1/clusters/{clusterName}", nonExistentCluster)
        .then()
            .statusCode(404);
    }

}