package com.rokkon.pipeline.consul.config;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.WithTestResource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that verifies Consul configuration loading with seeded data.
 * This test uses a custom test resource to seed Consul before the application starts.
 */
@QuarkusIntegrationTest
@WithTestResource(ConsulSeededTestResource.class)
public class ConsulSeededConfigIT {

    @Test
    void testConsulConfigLoaded() throws Exception {
        // Since we can't use @Inject in integration tests,
        // we verify the config was loaded by checking that the app started successfully
        // and by reading the config back from Consul
        
        // Get the Consul host/port from system properties
        String consulHost = System.getProperty("pipeline.consul.host", "localhost");
        String consulPort = System.getProperty("pipeline.consul.port", "8500");
        
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        
        // Read the seeded config from Consul
        URI getUri = URI.create(String.format("http://%s:%s/v1/kv/config/application?raw", 
            consulHost, consulPort));
        HttpRequest getRequest = HttpRequest.newBuilder()
            .uri(getUri)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode(), "Should be able to read seeded config from Consul");
        
        String configJson = getResponse.body();
        assertTrue(configJson.contains("test-value-from-consul"));
        assertTrue(configJson.contains("42"));
        assertTrue(configJson.contains("test-engine"));
        
        System.out.println("Successfully verified seeded configuration in Consul: " + configJson);
    }

    @Test
    void testHealthEndpointWithSeededConfig() {
        // Verify the application started successfully with seeded Consul config
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
    
    @Test
    void testApplicationStartedWithConsulConfig() {
        // Test that the application can access its endpoints, proving it started with Consul config
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(200);
    }
}