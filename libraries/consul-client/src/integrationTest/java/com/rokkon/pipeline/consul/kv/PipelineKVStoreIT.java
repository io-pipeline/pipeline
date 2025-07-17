package com.rokkon.pipeline.consul.kv;

import com.rokkon.pipeline.consul.profile.WithConsulConfigProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for pipeline KV store operations.
 * Tests the pipeline configuration storage and retrieval through REST APIs.
 */
@QuarkusIntegrationTest
@TestProfile(WithConsulConfigProfile.class)
public class PipelineKVStoreIT {
    
    @BeforeEach
    void setup() {
        // Ensure we have a clean state - create test cluster with proper content type
        given()
            .contentType(ContentType.JSON)
            .body("{}")  // Empty JSON body
            .when()
            .post("/api/v1/clusters/test-cluster")
            .then()
            .statusCode(anyOf(is(200), is(201), is(409))); // 409 if already exists
    }

    @Test
    void testCreateAndRetrievePipelineConfig() {
        // Create a test pipeline configuration with proper structure
        String pipelineConfig = """
            {
              "metadata": {
                "name": "test-pipeline",
                "version": "1.0.0",
                "description": "Integration test pipeline"
              },
              "steps": {
                "step1": {
                  "name": "step1",
                  "type": "INITIAL_PIPELINE",
                  "processorInfo": {
                    "className": "com.example.TestProcessor",
                    "version": "1.0.0"
                  },
                  "outputs": [
                    {
                      "name": "output1",
                      "type": "KAFKA",
                      "destination": "test-topic"
                    }
                  ]
                }
              },
              "globalSettings": {
                "defaultRetryAttempts": 3,
                "defaultTimeout": "PT30S"
              }
            }
            """;
        
        // Store pipeline config via REST API
        given()
            .contentType(ContentType.JSON)
            .body(pipelineConfig)
            .when()
            .post("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(anyOf(is(200), is(201)));
        
        // Retrieve the pipeline config
        given()
            .when()
            .get("/api/v1/clusters/test-cluster/pipelines/test-pipeline")
            .then()
            .statusCode(200)
            .body("metadata.name", equalTo("test-pipeline"))
            .body("metadata.version", equalTo("1.0.0"))
            .body("steps", notNullValue());
    }

    @Test
    void testClusterOperations() throws Exception {
        // Create a cluster using the correct endpoint
        given()
            .when()
            .post("/api/v1/clusters/integration-test-cluster")
            .then()
            .statusCode(anyOf(is(200), is(201)));
        
        // List clusters
        given()
            .when()
            .get("/api/v1/clusters")
            .then()
            .statusCode(200)
            .body("$", hasItem("integration-test-cluster"));
        
        // Verify data is actually in Consul KV
        String consulHost = System.getProperty("pipeline.consul.host", "localhost");
        String consulPort = System.getProperty("pipeline.consul.port", "8500");
        
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
    }

}