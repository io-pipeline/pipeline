package io.pipeline.engine;

import io.pipeline.api.model.ModuleWhitelistRequest;
import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.data.util.json.TestPipelineGenerator;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Manual integration test for testing the complete pipeline configuration flow using REST APIs.
 * This test assumes the pipestream-engine application is already running on port 8081.
 * It verifies that TestPipelineGenerator configurations can be successfully
 * stored in Consul through the proper service APIs.
 * 
 * Uses unique cluster names with timestamps for isolation.
 * 
 * Test Flow:
 * 1. Create test cluster with unique name
 * 2. Whitelist required services (chunker, embedder) 
 * 3. Create pipelines using TestPipelineGenerator data
 * 4. Verify configurations are stored correctly in Consul
 * 
 * Run this test only when pipestream-engine is running on port 8081.
 */
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Pipeline Configuration Manual Integration Test")
public class PipelineConfigManualTest {
    
    private static final String BASE_API_PATH = "/api/v1";
    private static String testClusterName;
    
    private PipelineClusterConfig testCluster;
    private PipelineConfig filesystemPipeline;
    private PipelineConfig echoPipeline;
    private PipelineConfig defaultPipeline;
    
    @BeforeAll
    static void setUpClass() {
        // Configure REST Assured to point to the running application
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 38100;
        
        // Generate unique cluster name for this test run
        testClusterName = "test-cluster-" + System.currentTimeMillis();
        
        System.out.println("Using test cluster name: " + testClusterName);
    }
    
    @BeforeEach
    void setUp() {
        // Generate test data using TestPipelineGenerator
        testCluster = TestPipelineGenerator.createTestCluster();
        filesystemPipeline = TestPipelineGenerator.createFilesystemPipeline();
        echoPipeline = TestPipelineGenerator.createEchoPipeline();
        defaultPipeline = TestPipelineGenerator.createDefaultPipeline();
        
        System.out.println("Generated test data for cluster: " + testClusterName);
    }
    
    @Test
    @Order(1)
    @DisplayName("Should create test cluster successfully")
    void shouldCreateTestCluster() {
        System.out.println("Creating test cluster: " + testClusterName);
        
        given()
            .contentType(ContentType.JSON)
            .body(testCluster)
        .when()
            .post(BASE_API_PATH + "/clusters/" + testClusterName)
        .then()
            .statusCode(anyOf(is(201), is(200), is(400))); // Created, OK, or already exists
    }
    
    @Test
    @Order(2)
    @DisplayName("Should whitelist chunker service successfully")
    void shouldWhitelistChunkerService() {
        System.out.println("Whitelisting chunker service for cluster: " + testClusterName);
        
        ModuleWhitelistRequest chunkerRequest = new ModuleWhitelistRequest(
            "Chunker service for document processing",
            "chunker"
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(chunkerRequest)
        .when()
            .post(BASE_API_PATH + "/clusters/" + testClusterName + "/whitelist")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("grpcServiceName", is("chunker"));
    }
    
    @Test
    @Order(3)
    @DisplayName("Should whitelist embedder service successfully")
    void shouldWhitelistEmbedderService() {
        System.out.println("Whitelisting embedder service for cluster: " + testClusterName);
        
        ModuleWhitelistRequest embedderRequest = new ModuleWhitelistRequest(
            "Embedder service for vector processing",
            "embedder"
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(embedderRequest)
        .when()
            .post(BASE_API_PATH + "/clusters/" + testClusterName + "/whitelist")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("grpcServiceName", is("embedder"));
    }
    
    @Test
    @Order(4)
    @DisplayName("Should create filesystem pipeline successfully")
    void shouldCreateFilesystemPipeline() {
        System.out.println("Creating filesystem pipeline for cluster: " + testClusterName);
        
        given()
            .contentType(ContentType.JSON)
            .body(filesystemPipeline)
        .when()
            .post(BASE_API_PATH + "/clusters/" + testClusterName + "/pipelines/filesystem-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(200)))
            .body("valid", is(true))
            .body("errors", is(empty())); // Should pass validation
    }
    
    @Test
    @Order(5)
    @DisplayName("Should create echo pipeline successfully")
    void shouldCreateEchoPipeline() {
        System.out.println("Creating echo pipeline for cluster: " + testClusterName);
        
        given()
            .contentType(ContentType.JSON)
            .body(echoPipeline)
        .when()
            .post(BASE_API_PATH + "/clusters/" + testClusterName + "/pipelines/echo-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(200)))
            .body("valid", is(true))
            .body("errors", is(empty()));
    }
    
    @Test
    @Order(6)
    @DisplayName("Should create default pipeline successfully")
    void shouldCreateDefaultPipeline() {
        System.out.println("Creating default pipeline for cluster: " + testClusterName);
        
        given()
            .contentType(ContentType.JSON)
            .body(defaultPipeline)
        .when()
            .post(BASE_API_PATH + "/clusters/" + testClusterName + "/pipelines/default-pipeline")
        .then()
            .statusCode(anyOf(is(201), is(200)))
            .body("valid", is(true))
            .body("errors", is(empty()));
    }
    
    @Test
    @Order(7)
    @DisplayName("Should retrieve created filesystem pipeline from Consul")
    void shouldRetrieveFilesystemPipeline() {
        System.out.println("Retrieving filesystem pipeline for cluster: " + testClusterName);
        
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + testClusterName + "/pipelines/filesystem-pipeline")
        .then()
            .statusCode(200)
            .body("pipelineName", is("filesystem-pipeline"))
            .body("steps", hasKey("chunker-step"))
            .body("steps", hasKey("embedder-sink"))
            .body("steps.size()", is(2));
    }
    
    @Test
    @Order(8)
    @DisplayName("Should verify filesystem pipeline has correct step configuration")
    void shouldVerifyFilesystemPipelineConfiguration() {
        System.out.println("Verifying filesystem pipeline configuration for cluster: " + testClusterName);
        
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + testClusterName + "/pipelines/filesystem-pipeline")
        .then()
            .statusCode(200)
            // Verify chunker step configuration
            .body("steps.'chunker-step'.stepType", is("PIPELINE"))
            .body("steps.'chunker-step'.processorInfo.grpcServiceName", is("chunker"))
            .body("steps.'chunker-step'.outputs", hasKey("default"))
            // Verify embedder sink configuration
            .body("steps.'embedder-sink'.stepType", is("SINK"))
            .body("steps.'embedder-sink'.processorInfo.grpcServiceName", is("embedder"))
            .body("steps.'embedder-sink'.outputs", is(empty()));
    }
    
    @Test
    @Order(9)
    @DisplayName("Should list all pipelines in cluster")
    void shouldListAllPipelinesInCluster() {
        System.out.println("Listing all pipelines for cluster: " + testClusterName);
        
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + testClusterName + "/pipelines")
        .then()
            .statusCode(200)
            .body("size()", is(3))
            .body("", hasItems("filesystem-pipeline", "echo-pipeline", "default-pipeline"));
    }
    
    @Test
    @Order(10)
    @DisplayName("Should verify cluster whitelist contains required services")
    void shouldVerifyClusterWhitelist() {
        System.out.println("Verifying cluster whitelist for cluster: " + testClusterName);
        
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + testClusterName + "/whitelist")
        .then()
            .statusCode(200)
            .body("allowedGrpcServices", hasItems("chunker", "embedder"))
            .body("allowedGrpcServices.size()", greaterThanOrEqualTo(2));
    }
    
    @Test
    @Order(11)
    @DisplayName("Should validate TestPipelineGenerator produces validation-compliant configurations")
    void shouldValidateTestPipelineGeneratorConfigurations() {
        System.out.println("Validating TestPipelineGenerator configurations for cluster: " + testClusterName);
        
        // Test that our TestPipelineGenerator produces configurations that pass validation
        // This is a meta-test to ensure our test data is solid
        
        // Verify filesystem pipeline structure
        given()
            .accept(ContentType.JSON)
        .when()
            .get(BASE_API_PATH + "/clusters/" + testClusterName + "/pipelines/filesystem-pipeline")
        .then()
            .statusCode(200)
            .body("pipelineName", is(notNullValue()))
            .body("steps", is(not(empty())))
            .body("steps.'chunker-step'.processorInfo.grpcServiceName", is("chunker"))
            .body("steps.'embedder-sink'.processorInfo.grpcServiceName", is("embedder"));
    }
}