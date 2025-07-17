package com.rokkon.echo;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration test that runs against the packaged application (JAR, native, or container).
 * 
 * According to TESTING_STRATEGY.md:
 * - Integration tests verify component interactions with real dependencies
 * - Use @QuarkusIntegrationTest for production-like testing
 * - Tests should be in src/integrationTest/java and end with IT
 * - Cannot inject beans as the test runs against a separate process
 * - Tests the application as a black box through exposed endpoints
 */
@QuarkusIntegrationTest
class EchoServiceIT {
    
    // Integration tests use REST endpoints to test the running application
    // They cannot inject beans or use @GrpcClient as the test runs against
    // a packaged JAR in a separate process
    
    @Test
    void testHealthEndpoint() {
        RestAssured.when()
            .get("/health")
            .then()
            .statusCode(200);
    }
    
    @Test
    void testReadyEndpoint() {
        RestAssured.when()
            .get("/health/ready")
            .then()
            .statusCode(200);
    }
    
    @Test
    void testLiveEndpoint() {
        RestAssured.when()
            .get("/health/live")
            .then()
            .statusCode(200);
    }
    
    @Test
    void testEchoResourceEndpoint() {
        // Test the echo resource endpoint
        RestAssured.given()
            .queryParam("message", "Hello Integration Test")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body(is("Echo: Hello Integration Test"));
    }
    
    @Test
    void testEchoStatusEndpoint() {
        // Test the status endpoint
        RestAssured.when()
            .get("/echo/status")
            .then()
            .statusCode(200)
            .body("service", is("echo"))
            .body("status", is("healthy"))
            .body("timestamp", notNullValue());
    }
    
    @Test
    void testDebugInfoEndpoint() {
        // Test the debug info endpoint
        RestAssured.when()
            .get("/debug/info")
            .then()
            .statusCode(200)
            .body("module", is("echo"))
            .body("serviceInfo", notNullValue())
            .body("serviceInfo.moduleName", is("echo"))
            .body("serviceInfo.healthCheckPassed", is(true));
    }
    
    @Test
    void testDebugTestEndpoint() {
        // Test the debug test endpoint which uses the gRPC service internally
        RestAssured.when()
            .get("/debug/test")
            .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", containsString("test echo service"));
    }
}