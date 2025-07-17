
package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.config.service.ClusterService;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public abstract class ClusterResourceTestBase {

    protected abstract ClusterService getClusterService();

    @Test
    void testCreateCluster() {
        String clusterName = "test-create-cluster";
        given()
          .contentType("application/json")
          .pathParam("clusterName", clusterName)
        .when()
          .post("/api/v1/clusters/{clusterName}")
        .then()
           .statusCode(201);
    }

    @Test
    void testGetCluster() {
        String clusterName = "test-get-cluster";
        given()
          .pathParam("clusterName", clusterName)
        .when()
          .get("/api/v1/clusters/{clusterName}")
        .then()
           .statusCode(200);
    }

    @Test
    void testDeleteCluster() {
        String clusterName = "test-delete-cluster";
        given()
          .pathParam("clusterName", clusterName)
        .when()
          .delete("/api/v1/clusters/{clusterName}")
        .then()
           .statusCode(200);
    }
    
    @Test
    void testGetNonExistentCluster() {
        String clusterName = "non-existent-cluster";
        given()
          .pathParam("clusterName", clusterName)
        .when()
          .get("/api/v1/clusters/{clusterName}")
        .then()
           .statusCode(404);
    }
}

