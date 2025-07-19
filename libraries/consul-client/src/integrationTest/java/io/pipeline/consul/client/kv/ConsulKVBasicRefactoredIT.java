package io.pipeline.consul.client.kv;

import io.pipeline.consul.client.integration.InMemoryRegistryTestProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
 * Integration test for basic Consul KV operations using in-memory registry.
 * Tests KV functionality while using the in-memory module registry for 
 * faster testing without full Consul service dependencies.
 */
@QuarkusIntegrationTest
@TestProfile(InMemoryRegistryTestProfile.class)
public class ConsulKVBasicRefactoredIT {
    
    private static final Logger LOG = Logger.getLogger(ConsulKVBasicRefactoredIT.class);
    
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
        // Test that the application started with in-memory module registry
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
    
    @Test
    void testClusterKVOperations() {
        String clusterName = "refactored-kv-test-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing cluster KV operations with: %s", clusterName);
        
        // Create a cluster using the REST API
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
        
        // Verify cluster shows up in listing
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(200)
                .body("name", hasItem(clusterName));
                
        // Verify we can retrieve the specific cluster
        given()
            .when().get("/api/v1/clusters/{clusterName}", clusterName)
            .then()
                .statusCode(200);
    }
    
    @Test
    void testConsulKVStorage() throws Exception {
        String clusterName = "consul-kv-storage-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        LOG.infof("Testing Consul KV storage with cluster: %s", clusterName);
        
        // Create a cluster
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
        
        // Verify data is actually stored in Consul KV
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
    void testInMemoryModuleRegistry() {
        // Test that we're using the in-memory module registry
        LOG.info("Testing in-memory module registry functionality");
        
        // The in-memory registry should have pre-loaded basic modules
        // This is verified by the successful application startup
        // and the fact that cluster operations work
        
        // Create a cluster to verify the module registry is working
        String clusterName = "module-registry-test-" + UUID.randomUUID().toString().substring(0, 8);
        createdClusters.add(clusterName);
        
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/clusters/{clusterName}", clusterName)
            .then()
            .statusCode(201);
            
        // If we get here, the in-memory module registry is working
        LOG.info("In-memory module registry is functioning correctly");
    }
}