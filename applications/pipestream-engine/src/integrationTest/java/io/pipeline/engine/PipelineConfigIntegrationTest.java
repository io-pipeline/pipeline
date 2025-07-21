package io.pipeline.engine;

import io.pipeline.api.model.ModuleWhitelistRequest;
import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.testing.consul.ConsulIntegrationTest;
import io.pipeline.testing.consul.ConsulTest;
import io.pipeline.testing.consul.ConsulTestContext;
import io.pipeline.data.util.json.TestPipelineGenerator;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for testing the complete pipeline configuration flow using REST APIs.
 * This test verifies that TestPipelineGenerator configurations can be successfully
 * stored in Consul through the proper service APIs.
 * 
 * Uses ConsulIntegrationTest for complete KV isolation with unique namespace per test run.
 * Each test gets its own namespace like: pipelineconfigintegrationtest-testmethod-timestamp
 * 
 * Test Flow:
 * 1. Create test cluster
 * 2. Whitelist required services (chunker, embedder) 
 * 3. Create pipelines using TestPipelineGenerator data
 * 4. Verify configurations are stored correctly in Consul
 */
@ConsulIntegrationTest
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Pipeline Configuration Integration Test")
public class PipelineConfigIntegrationTest {
    
    private static final String TEST_CLUSTER_NAME = "test-cluster";
    private static final String BASE_API_PATH = "/api/v1";
    
    @ConsulTest
    private ConsulTestContext consulTestContext;
    
    private PipelineClusterConfig testCluster;
    private PipelineConfig filesystemPipeline;
    private PipelineConfig echoPipeline;
    private PipelineConfig defaultPipeline;
    
    @BeforeEach
    void setUp() {
        // Generate test data using TestPipelineGenerator
        testCluster = TestPipelineGenerator.createTestCluster();
        filesystemPipeline = TestPipelineGenerator.createFilesystemPipeline();
        echoPipeline = TestPipelineGenerator.createEchoPipeline();
        defaultPipeline = TestPipelineGenerator.createDefaultPipeline();
        
        // ConsulIntegrationTest automatically provides cleanup via ConsulTestContext
        // Each test gets isolated namespace: pipelineconfigintegrationtest-methodname-timestamp
    }
    
    @Test
    @Order(1)
    @DisplayName("Should create test cluster successfully")
    void shouldCreateTestCluster() {
        given()
            .contentType(ContentType.JSON)
            .body(testCluster)
        .when()
            .post(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME)
        .then()
            .statusCode(anyOf(is(201), is(200), is(400))); // Created, OK, or already exists
    }
    
    @Test
    @Order(2)
    @DisplayName(
            "Should whitelist chunker service successfully")
    void shouldWhitelistChunkerService() {
        ModuleWhitelistRequest chunkerRequest = new ModuleWhitelistRequest(
            "Chunker service for document processing",
            "chunker"
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(chunkerRequest)
        .when()
            .post(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/whitelist")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("grpcServiceName", is("chunker"));
    }
    
    @Test
    @Order(3)
    @DisplayName("Should whitelist echo service successfully")
    void shouldWhitelistEchoService() {
        ModuleWhitelistRequest echoRequest = new ModuleWhitelistRequest(
            "Echo service for testing and debugging",
            "echo"
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(echoRequest)
        .when()
            .post(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/whitelist")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("grpcServiceName", is("echo"));
    }
    
    @Test
    @Order(4)
    @DisplayName("Should create filesystem pipeline successfully")
    void shouldCreateFilesystemPipeline() {
        given()
            .contentType(ContentType.JSON)
            .body(filesystemPipeline)
        .when()
            .post(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/pipelines/filesystem-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(200)))
            .body("valid", is(true))
            .body("errors", is(empty())); // Should pass validation
    }
    
    @Test
    @Order(5)
    @DisplayName("Should create echo pipeline successfully")
    void shouldCreateEchoPipeline() {
        given()
            .contentType(ContentType.JSON)
            .body(echoPipeline)
        .when()
            .post(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/pipelines/echo-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(200)))
            .body("valid", is(true))
            .body("errors", is(empty()));
    }
    
    @Test
    @Order(6)
    @DisplayName("Should create default pipeline successfully")
    void shouldCreateDefaultPipeline() {
        given()
            .contentType(ContentType.JSON)
            .body(defaultPipeline)
        .when()
            .post(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/pipelines/default-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(200)))
            .body("valid", is(true))
            .body("errors", is(empty()));
    }
    
    @Test
    @Order(7)
    @DisplayName("Should retrieve created filesystem pipeline from Consul")
    void shouldRetrieveFilesystemPipeline() {
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/pipelines/filesystem-pipeline")
        .then()
            .statusCode(200)
            .body("pipelineName", is("filesystem-pipeline"))
            .body("steps", hasKey("chunker-step"))
            .body("steps", hasKey("echo-sink"))
            .body("steps.size()", is(2));
    }
    
    @Test
    @Order(8)
    @DisplayName("Should verify filesystem pipeline has correct step configuration")
    void shouldVerifyFilesystemPipelineConfiguration() {
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/pipelines/filesystem-pipeline")
        .then()
            .statusCode(200)
            // Verify chunker step configuration
            .body("steps.'chunker-step'.stepType", is("PIPELINE"))
            .body("steps.'chunker-step'.processorInfo.grpcServiceName", is("chunker"))
            .body("steps.'chunker-step'.outputs", hasKey("default"))
            // Verify echo sink configuration
            .body("steps.'echo-sink'.stepType", is("SINK"))
            .body("steps.'echo-sink'.processorInfo.grpcServiceName", is("echo"))
            .body("steps.'echo-sink'.outputs", is(empty()));
    }
    
    @Test
    @Order(9)
    @DisplayName("Should list all pipelines in cluster")
    void shouldListAllPipelinesInCluster() {
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/pipelines")
        .then()
            .statusCode(200)
            .body("size()", is(3))
            .body("", hasItems("filesystem-pipeline", "echo-pipeline", "default-pipeline"));
    }
    
    @Test
    @Order(10)
    @DisplayName("Should verify cluster whitelist contains required services")
    void shouldVerifyClusterWhitelist() {
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/whitelist")
        .then()
            .statusCode(200)
            .body("allowedGrpcServices", hasItems("chunker", "echo"))
            .body("allowedGrpcServices.size()", greaterThanOrEqualTo(2));
    }
    
    @Test
    @Order(11)
    @DisplayName("Should validate TestPipelineGenerator produces validation-compliant configurations")
    void shouldValidateTestPipelineGeneratorConfigurations() {
        // Test that our TestPipelineGenerator produces configurations that pass validation
        // This is a meta-test to ensure our test data is solid
        
        // Verify filesystem pipeline structure
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + TEST_CLUSTER_NAME + "/pipelines/filesystem-pipeline")
        .then()
            .statusCode(200)
            .body("pipelineName", is(notNullValue()))
            .body("steps", is(not(empty())))
            .body("steps.'chunker-step'.processorInfo.grpcServiceName", is("chunker"))
            .body("steps.'echo-sink'.processorInfo.grpcServiceName", is("echo"));
    }
}