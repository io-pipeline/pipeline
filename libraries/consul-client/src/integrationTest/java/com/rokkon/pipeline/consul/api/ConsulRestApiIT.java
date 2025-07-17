package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.consul.test.ConsulIntegrationTest;
import com.rokkon.pipeline.consul.test.ConsulTest;
import com.rokkon.pipeline.consul.test.ConsulTestContext;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for the Consul REST APIs.
 * This test verifies that the REST endpoints work correctly with a real Consul instance.
 */
@ConsulIntegrationTest
class ConsulRestApiIT {
    
    @ConsulTest
    ConsulTestContext consul;
    
    @BeforeEach
    void setup() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    @Test
    void testClusterCrudOperations() {
        String clusterName = "test-cluster-" + System.currentTimeMillis();
        
        // Create cluster
        given()
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(201)
            .body("valid", is(true));
            
        // List clusters
        given()
            .when()
            .get("/api/v1/clusters")
            .then()
            .statusCode(200)
            .body("$", hasKey(clusterName));
            
        // Get specific cluster
        given()
            .when()
            .get("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(200);
            
        // Delete cluster
        given()
            .when()
            .delete("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(200)
            .body("valid", is(true));
    }
    
    @Test
    void testPipelineConfigCrudOperations() {
        String clusterName = "test-cluster-" + System.currentTimeMillis();
        String pipelineId = "test-pipeline-" + System.currentTimeMillis();
        
        // First create a cluster
        given()
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/v1/clusters/" + clusterName)
            .then()
            .statusCode(201);
            
        // Create pipeline
        String pipelineJson = """
            {
                "name": "%s",
                "pipelineSteps": {}
            }
            """.formatted(pipelineId);
            
        given()
            .contentType("application/json")
            .body(pipelineJson)
            .when()
            .post("/api/v1/clusters/" + clusterName + "/pipelines/" + pipelineId)
            .then()
            .statusCode(201)
            .body("valid", is(true));
            
        // List pipelines
        given()
            .when()
            .get("/api/v1/clusters/" + clusterName + "/pipelines")
            .then()
            .statusCode(200)
            .body("$", hasKey(pipelineId));
            
        // Get specific pipeline
        given()
            .when()
            .get("/api/v1/clusters/" + clusterName + "/pipelines/" + pipelineId)
            .then()
            .statusCode(200)
            .body("name", is(pipelineId));
            
        // Delete pipeline
        given()
            .when()
            .delete("/api/v1/clusters/" + clusterName + "/pipelines/" + pipelineId)
            .then()
            .statusCode(200)
            .body("valid", is(true));
    }
    
    @Test
    void testHealthCheck() {
        given()
            .when()
            .get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", is("UP"));
    }
}