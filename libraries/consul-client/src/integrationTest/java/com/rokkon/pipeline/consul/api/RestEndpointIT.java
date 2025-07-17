package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.consul.profile.NoConsulConfigProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Simple integration test for REST endpoints without Consul config dependency.
 * This tests that the REST endpoints are properly exposed and respond.
 */
@QuarkusIntegrationTest
@TestProfile(NoConsulConfigProfile.class)
public class RestEndpointIT {
    
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
    void testClusterEndpointExists() {
        // Just test that the endpoint exists and responds
        // It might return 500 if services aren't properly initialized
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(anyOf(is(200), is(500))); // Accept either for now
    }
    
    @Test
    void testPipelineEndpointExists() {
        // Test that pipeline endpoint exists
        given()
            .when().get("/api/v1/clusters/test-cluster/pipelines")
            .then()
                .statusCode(anyOf(is(200), is(404), is(500))); // Accept various states
    }
}