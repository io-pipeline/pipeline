package io.pipeline.engine;

import io.pipeline.api.model.ModuleWhitelistRequest;
import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.data.util.json.TestPipelineGenerator;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for testing the complete pipeline configuration flow.
 * This provides endpoints to create clusters, whitelist services, and create pipelines
 * using TestPipelineGenerator data for end-to-end testing.
 */
@Path("/api/test/pipeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PipelineTestResource {
    
    private static final Logger LOG = Logger.getLogger(PipelineTestResource.class);
    
    /**
     * Create a test cluster with unique timestamp-based naming.
     * Uses TestPipelineGenerator.createTestCluster() for real service data.
     */
    @POST
    @Path("/cluster")
    public Response createTestCluster(@QueryParam("clusterName") String clusterName) {
        if (clusterName == null || clusterName.trim().isEmpty()) {
            clusterName = "test-cluster-" + System.currentTimeMillis();
        }
        
        LOG.infof("Creating test cluster: %s", clusterName);
        
        try {
            PipelineClusterConfig testCluster = TestPipelineGenerator.createTestCluster();
            
            TestClusterResult result = new TestClusterResult();
            result.clusterName = clusterName;
            result.success = true;
            result.message = "Test cluster configuration generated successfully";
            result.clusterConfig = testCluster;
            result.requiredServices = List.of("chunker", "embedder");
            result.includedPipelines = List.of("filesystem-pipeline", "echo-pipeline", "default-pipeline");
            
            LOG.infof("Successfully created test cluster configuration: %s", clusterName);
            return Response.ok(result).build();
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create test cluster: %s", clusterName);
            
            TestClusterResult result = new TestClusterResult();
            result.clusterName = clusterName;
            result.success = false;
            result.message = "Failed to create test cluster: " + e.getMessage();
            
            return Response.serverError().entity(result).build();
        }
    }
    
    /**
     * Get whitelist requests for the services needed by TestPipelineGenerator configurations.
     */
    @GET
    @Path("/whitelist-requests")
    public Response getWhitelistRequests() {
        LOG.info("Generating whitelist requests for TestPipelineGenerator services");
        
        try {
            List<ModuleWhitelistRequest> requests = new ArrayList<>();
            
            // Add chunker service - used by filesystem-pipeline
            requests.add(new ModuleWhitelistRequest(
                "Chunker service for document processing in test pipelines",
                "chunker"
            ));
            
            // Add embedder service - used as sink in updated filesystem-pipeline  
            requests.add(new ModuleWhitelistRequest(
                "Embedder service for vector processing in test pipelines",
                "embedder"
            ));
            
            WhitelistRequestsResult result = new WhitelistRequestsResult();
            result.success = true;
            result.message = "Generated whitelist requests for TestPipelineGenerator services";
            result.requests = requests;
            result.serviceCount = requests.size();
            
            LOG.infof("Generated %d whitelist requests", requests.size());
            return Response.ok(result).build();
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate whitelist requests");
            
            WhitelistRequestsResult result = new WhitelistRequestsResult();
            result.success = false;
            result.message = "Failed to generate whitelist requests: " + e.getMessage();
            
            return Response.serverError().entity(result).build();
        }
    }
    
    /**
     * Get all pipeline configurations from TestPipelineGenerator.
     */
    @GET
    @Path("/pipelines")
    public Response getTestPipelines() {
        LOG.info("Generating test pipeline configurations");
        
        try {
            Map<String, PipelineConfig> pipelines = new HashMap<>();
            
            pipelines.put("filesystem-pipeline", TestPipelineGenerator.createFilesystemPipeline());
            pipelines.put("echo-pipeline", TestPipelineGenerator.createEchoPipeline());
            pipelines.put("default-pipeline", TestPipelineGenerator.createDefaultPipeline());
            
            TestPipelinesResult result = new TestPipelinesResult();
            result.success = true;
            result.message = "Generated test pipeline configurations from TestPipelineGenerator";
            result.pipelines = pipelines;
            result.pipelineCount = pipelines.size();
            result.requiredServices = List.of("chunker", "embedder");
            
            LOG.infof("Generated %d test pipeline configurations", pipelines.size());
            return Response.ok(result).build();
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate test pipelines");
            
            TestPipelinesResult result = new TestPipelinesResult();
            result.success = false;
            result.message = "Failed to generate test pipelines: " + e.getMessage();
            
            return Response.serverError().entity(result).build();
        }
    }
    
    /**
     * Get a specific pipeline configuration by name.
     */
    @GET
    @Path("/pipelines/{pipelineName}")
    public Response getTestPipeline(@PathParam("pipelineName") String pipelineName) {
        LOG.infof("Getting test pipeline configuration: %s", pipelineName);
        
        try {
            PipelineConfig pipeline = switch (pipelineName) {
                case "filesystem-pipeline" -> TestPipelineGenerator.createFilesystemPipeline();
                case "echo-pipeline" -> TestPipelineGenerator.createEchoPipeline();
                case "default-pipeline" -> TestPipelineGenerator.createDefaultPipeline();
                default -> null;
            };
            
            if (pipeline == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of(
                        "success", false,
                        "message", "Unknown pipeline name: " + pipelineName,
                        "availablePipelines", List.of("filesystem-pipeline", "echo-pipeline", "default-pipeline")
                    ))
                    .build();
            }
            
            TestPipelineResult result = new TestPipelineResult();
            result.success = true;
            result.message = "Generated test pipeline configuration: " + pipelineName;
            result.pipelineName = pipelineName;
            result.pipeline = pipeline;
            result.requiredServices = extractRequiredServices(pipeline);
            
            LOG.infof("Successfully generated test pipeline: %s", pipelineName);
            return Response.ok(result).build();
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate test pipeline: %s", pipelineName);
            
            TestPipelineResult result = new TestPipelineResult();
            result.success = false;
            result.message = "Failed to generate test pipeline: " + e.getMessage();
            result.pipelineName = pipelineName;
            
            return Response.serverError().entity(result).build();
        }
    }
    
    /**
     * Run a complete test flow: create cluster, get whitelist requests, get pipelines.
     * This simulates what the integration test does.
     */
    @POST
    @Path("/complete-flow")
    public Response runCompleteTestFlow(@QueryParam("clusterName") String clusterName) {
        if (clusterName == null || clusterName.trim().isEmpty()) {
            clusterName = "complete-test-" + System.currentTimeMillis();
        }
        
        LOG.infof("Running complete test flow for cluster: %s", clusterName);
        
        try {
            // Step 1: Create cluster configuration
            PipelineClusterConfig testCluster = TestPipelineGenerator.createTestCluster();
            
            // Step 2: Generate whitelist requests
            List<ModuleWhitelistRequest> whitelistRequests = List.of(
                new ModuleWhitelistRequest("Chunker service for test pipelines", "chunker"),
                new ModuleWhitelistRequest("Embedder service for test pipelines", "embedder")
            );
            
            // Step 3: Generate pipeline configurations
            Map<String, PipelineConfig> pipelines = Map.of(
                "filesystem-pipeline", TestPipelineGenerator.createFilesystemPipeline(),
                "echo-pipeline", TestPipelineGenerator.createEchoPipeline(),
                "default-pipeline", TestPipelineGenerator.createDefaultPipeline()
            );
            
            CompleteTestFlowResult result = new CompleteTestFlowResult();
            result.success = true;
            result.message = "Complete test flow executed successfully";
            result.clusterName = clusterName;
            result.timestamp = Instant.now().toString();
            result.clusterConfig = testCluster;
            result.whitelistRequests = whitelistRequests;
            result.pipelines = pipelines;
            result.requiredServices = List.of("chunker", "embedder");
            result.nextSteps = List.of(
                "1. POST cluster config to /api/v1/clusters/" + clusterName,
                "2. POST each whitelist request to /api/v1/clusters/" + clusterName + "/whitelist",
                "3. POST each pipeline to /api/v1/clusters/" + clusterName + "/pipelines/{pipelineName}",
                "4. GET pipelines from /api/v1/clusters/" + clusterName + "/pipelines to verify"
            );
            
            LOG.infof("Successfully completed test flow for cluster: %s", clusterName);
            return Response.ok(result).build();
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to run complete test flow for cluster: %s", clusterName);
            
            CompleteTestFlowResult result = new CompleteTestFlowResult();
            result.success = false;
            result.message = "Failed to run complete test flow: " + e.getMessage();
            result.clusterName = clusterName;
            result.timestamp = Instant.now().toString();
            
            return Response.serverError().entity(result).build();
        }
    }
    
    private List<String> extractRequiredServices(PipelineConfig pipeline) {
        return pipeline.pipelineSteps().values().stream()
            .map(step -> step.processorInfo().grpcServiceName())
            .distinct()
            .sorted()
            .toList();
    }
    
    // Result classes for JSON responses
    
    public static class TestClusterResult {
        public String clusterName;
        public boolean success;
        public String message;
        public PipelineClusterConfig clusterConfig;
        public List<String> requiredServices;
        public List<String> includedPipelines;
    }
    
    public static class WhitelistRequestsResult {
        public boolean success;
        public String message;
        public List<ModuleWhitelistRequest> requests;
        public int serviceCount;
    }
    
    public static class TestPipelinesResult {
        public boolean success;
        public String message;
        public Map<String, PipelineConfig> pipelines;
        public int pipelineCount;
        public List<String> requiredServices;
    }
    
    public static class TestPipelineResult {
        public boolean success;
        public String message;
        public String pipelineName;
        public PipelineConfig pipeline;
        public List<String> requiredServices;
    }
    
    public static class CompleteTestFlowResult {
        public boolean success;
        public String message;
        public String clusterName;
        public String timestamp;
        public PipelineClusterConfig clusterConfig;
        public List<ModuleWhitelistRequest> whitelistRequests;
        public Map<String, PipelineConfig> pipelines;
        public List<String> requiredServices;
        public List<String> nextSteps;
    }
}