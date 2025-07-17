package com.rokkon.pipeline.consul.config;

import com.rokkon.pipeline.consul.profile.WithConsulConfigProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for Consul configuration loading.
 * This test verifies that Consul connectivity works and we can store/retrieve configuration.
 */
@QuarkusIntegrationTest
@TestProfile(WithConsulConfigProfile.class)
public class ConsulConfigIT {

    @Test
    void testConsulConnectivity() throws Exception {
        // Get the DevServices Consul port from system properties
        String consulHost = System.getProperty("pipeline.consul.host", "localhost");
        String consulPort = System.getProperty("pipeline.consul.port", "8500");
        
        System.out.println("Testing Consul connectivity at " + consulHost + ":" + consulPort);
        
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        
        // First, seed a test value
        String testKey = "test/consul-client/integration";
        String testValue = "integration-test-value-" + System.currentTimeMillis();
        
        URI putUri = URI.create(String.format("http://%s:%s/v1/kv/%s", 
            consulHost, consulPort, testKey));
        HttpRequest putRequest = HttpRequest.newBuilder()
            .uri(putUri)
            .timeout(Duration.ofSeconds(5))
            .PUT(HttpRequest.BodyPublishers.ofString(testValue))
            .build();

        HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putResponse.statusCode(), "Should be able to write to Consul");
        
        // Now read it back
        URI getUri = URI.create(String.format("http://%s:%s/v1/kv/%s?raw", 
            consulHost, consulPort, testKey));
        HttpRequest getRequest = HttpRequest.newBuilder()
            .uri(getUri)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode(), "Should be able to read from Consul");
        assertEquals(testValue, getResponse.body(), "Value should match what was written");
        
        System.out.println("Successfully wrote and read from Consul KV store");
    }

    @Test
    void testHealthEndpointWithConsulConfig() {
        // Verify the application started successfully with Consul config enabled
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
    
    @Test
    void testConsulClientFactoryConnection() {
        // Test that our ConsulClientFactory can connect by hitting the cluster endpoint
        // This will exercise the ConsulClient injection
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(200);
    }
}