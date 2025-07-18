package io.pipeline.consul.client.kv;

import io.pipeline.consul.client.profile.WithConsulConfigProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Basic integration test to verify Consul KV store connectivity.
 */
@QuarkusIntegrationTest
@TestProfile(WithConsulConfigProfile.class)
public class ConsulKVBasicIT {

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
    void testListClusters() {
        // Test that we can list clusters (exercises ConsulClient)
        given()
            .when().get("/api/v1/clusters")
            .then()
                .statusCode(200);
    }
}