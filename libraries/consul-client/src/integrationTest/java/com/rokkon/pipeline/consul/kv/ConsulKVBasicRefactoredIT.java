package com.rokkon.pipeline.consul.kv;

import com.rokkon.pipeline.consul.test.ConsulIntegrationTest;
import com.rokkon.pipeline.consul.test.ConsulTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Example of refactored integration test using new pattern.
 * 
 * This test demonstrates:
 * - Using @ConsulIntegrationTest instead of @QuarkusIntegrationTest
 * - Implementing ConsulTestSupport for test utilities
 * - Namespace isolation per test class
 * - Proper cleanup
 */
@ConsulIntegrationTest(namespacePrefix = "consul-kv")
public class ConsulKVBasicRefactoredIT implements ConsulTestSupport {
    
    @BeforeEach
    void setup() {
        // Clean namespace is handled by the annotation
        // Any additional setup can go here
    }
    
    @AfterEach
    void cleanup() {
        // Optional: explicit cleanup if needed
        // The annotation handles cleanup by default
    }
    
    @Test
    void testConsulConnectivity() {
        // Test that the application started with Consul connection
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
    
    @Test
    void testKVOperations() {
        // Test KV operations using the test support interface
        String key = "test-key";
        String value = "test-value";
        
        // Put a value using the namespaced helper
        putValue(key, value);
        
        // Verify it exists
        assertTrue(getValue(key).isPresent());
        assertEquals(value, getValue(key).get());
        
        // Delete the key
        deleteKey(key);
        
        // Verify it's gone
        assertFalse(getValue(key).isPresent());
    }
    
    @Test
    void testListClusters() {
        // Create some test data in our namespace
        putValue("clusters/test-cluster/config", "{\"name\":\"test-cluster\"}");
        
        // Test that we can list clusters
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(200);
                
        // Note: The actual cluster listing might not see our namespaced data
        // depending on how the ClusterService is implemented
    }
    
    @Test
    void testServiceRegistration() {
        // Test waiting for services
        if (!hasService("consul")) {
            fail("Consul service should be available");
        }
        
        // If registration service is running, wait for it
        if (hasService("registration-service")) {
            // Test that echo module registered
            Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> hasService("echo"));
        }
    }
    
    @Test
    void testNamespaceIsolation() {
        // Test that each test class gets its own namespace
        String myKey = "isolation-test";
        putValue(myKey, "my-value");
        
        // The key should be namespaced
        String fullKey = namespacedKey(myKey);
        assertTrue(fullKey.startsWith("test/consul-kv/"));
        
        // Other tests in different classes won't see this key
    }
    
    @Test
    void testWaitForKey() {
        // Test the wait functionality
        String key = "delayed-key";
        
        // Put value after a delay using REST endpoint
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                putValue(key, "delayed-value");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        // Wait for the key to appear
        waitForKey(key, Duration.ofSeconds(5));
        
        // Verify it's there
        assertEquals("delayed-value", getValue(key).get());
    }
}