package io.pipeline.engine;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for PipeStreamEngine routing logic.
 * Uses REST endpoints to test routing components with real JSON objects.
 */
@QuarkusIntegrationTest
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("PipeStream Routing Integration Tests")
public class PipeStreamRoutingIT {

    @BeforeAll
    static void setUpClass() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 38100;
    }

    @Test
    @Order(1)
    @DisplayName("Should extract cluster and pipeline context from valid PipeStream")
    void shouldExtractValidContext() {
        String pipeStreamJson = """
            {
                "streamId": "test-stream-123",
                "currentPipelineName": "filesystem-pipeline",
                "targetStepName": "chunker-step",
                "contextParams": {
                    "cluster": "curl-test-1753062013"
                },
                "currentHopNumber": 0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(pipeStreamJson)
        .when()
            .post("/api/test/routing/extract-context")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("clusterName", is("curl-test-1753062013"))
            .body("pipelineName", is("filesystem-pipeline"))
            .body("streamId", is("test-stream-123"));
    }

    @Test
    @Order(2)
    @DisplayName("Should fail when cluster name missing from context params")
    void shouldFailWhenClusterMissing() {
        String pipeStreamJson = """
            {
                "streamId": "test-stream-456",
                "currentPipelineName": "filesystem-pipeline",
                "targetStepName": "chunker-step",
                "contextParams": {},
                "currentHopNumber": 0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(pipeStreamJson)
        .when()
            .post("/api/test/routing/extract-context")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("error", containsString("Cluster name not specified"))
            .body("streamId", is("test-stream-456"));
    }

    @Test
    @Order(3)
    @DisplayName("Should fail when pipeline name missing")
    void shouldFailWhenPipelineNameMissing() {
        String pipeStreamJson = """
            {
                "streamId": "test-stream-789",
                "targetStepName": "chunker-step",
                "contextParams": {
                    "cluster": "curl-test-1753062013"
                },
                "currentHopNumber": 0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(pipeStreamJson)
        .when()
            .post("/api/test/routing/extract-context")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("error", containsString("Pipeline name not specified"))
            .body("streamId", is("test-stream-789"));
    }

    @Test
    @Order(4)
    @DisplayName("Should retrieve step configuration successfully")
    void shouldRetrieveStepConfig() {
        given()
        .when()
            .get("/api/test/routing/step-config/curl-test-1753062013/filesystem-pipeline/chunker-step")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("clusterName", is("curl-test-1753062013"))
            .body("pipelineName", is("filesystem-pipeline"))
            .body("stepName", is("chunker-step"))
            .body("serviceName", is("chunker"))
            .body("stepConfig.stepName", is("chunker-step"))
            .body("stepConfig.processorInfo.grpcServiceName", is("chunker"));
    }

    @Test
    @Order(5)
    @DisplayName("Should fail when step not found")
    void shouldFailWhenStepNotFound() {
        given()
        .when()
            .get("/api/test/routing/step-config/curl-test-1753062013/filesystem-pipeline/non-existent-step")
        .then()
            .statusCode(404)
            .body("success", is(false))
            .body("error", containsString("Step not found"))
            .body("stepName", is("non-existent-step"));
    }

    @Test
    @Order(6)
    @DisplayName("Should check service availability")
    void shouldCheckServiceAvailability() {
        given()
        .when()
            .get("/api/test/routing/service-availability/chunker")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("serviceName", is("chunker"))
            .body("available", is(true))
            .body("message", containsString("Service client created successfully"));
    }

    @Test
    @Order(7)
    @DisplayName("Should fail for unavailable service")
    void shouldFailForUnavailableService() {
        given()
        .when()
            .get("/api/test/routing/service-availability/non-existent-service")
        .then()
            .statusCode(503)
            .body("success", is(false))
            .body("serviceName", is("non-existent-service"))
            .body("available", is(false))
            .body("error", is(notNullValue()));
    }

    @Test
    @Order(8)
    @DisplayName("Should validate complete routing successfully")
    void shouldValidateCompleteRouting() {
        String pipeStreamJson = """
            {
                "streamId": "routing-test-123",
                "currentPipelineName": "filesystem-pipeline",
                "targetStepName": "chunker-step",
                "contextParams": {
                    "cluster": "curl-test-1753062013"
                },
                "currentHopNumber": 0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(pipeStreamJson)
        .when()
            .post("/api/test/routing/validate-routing")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", is("Routing validation successful"))
            .body("context.clusterName", is("curl-test-1753062013"))
            .body("context.pipelineName", is("filesystem-pipeline"))
            .body("stepConfig.stepName", is("chunker-step"))
            .body("serviceName", is("chunker"))
            .body("serviceAvailable", is(true));
    }

    @Test
    @Order(9)
    @DisplayName("Should fail routing validation when target step missing")
    void shouldFailRoutingValidationWhenTargetStepMissing() {
        String pipeStreamJson = """
            {
                "streamId": "routing-test-456",
                "currentPipelineName": "filesystem-pipeline",
                "contextParams": {
                    "cluster": "curl-test-1753062013"
                },
                "currentHopNumber": 0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(pipeStreamJson)
        .when()
            .post("/api/test/routing/validate-routing")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("error", containsString("Target step name not specified"))
            .body("streamId", is("routing-test-456"));
    }

    @Test
    @Order(10)
    @DisplayName("Should fail routing validation for non-existent step")
    void shouldFailRoutingValidationForNonExistentStep() {
        String pipeStreamJson = """
            {
                "streamId": "routing-test-789",
                "currentPipelineName": "filesystem-pipeline",
                "targetStepName": "non-existent-step",
                "contextParams": {
                    "cluster": "curl-test-1753062013"
                },
                "currentHopNumber": 0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(pipeStreamJson)
        .when()
            .post("/api/test/routing/validate-routing")
        .then()
            .statusCode(404)
            .body("success", is(false))
            .body("error", containsString("Step not found"))
            .body("context.clusterName", is("curl-test-1753062013"))
            .body("context.pipelineName", is("filesystem-pipeline"));
    }
}