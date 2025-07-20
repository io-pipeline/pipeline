package io.pipeline.module.draft;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class DraftProcessorServiceTest {

    @Test
    public void testHealthEndpoint() {
        given()
          .when().get("/q/health")
          .then()
             .statusCode(200)
             .body("status", is("UP"));
    }

    @Test
    public void testReadyEndpoint() {
        given()
          .when().get("/q/health/ready")
          .then()
             .statusCode(200);
    }

    @Test
    public void testLiveEndpoint() {
        given()
          .when().get("/q/health/live")
          .then()
             .statusCode(200);
    }
}