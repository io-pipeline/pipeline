package io.pipeline.engine;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.StepType;
import io.pipeline.api.model.TransportType;
import io.pipeline.api.model.GrpcTransportConfig;
import io.pipeline.api.service.PipelineConfigService;
import io.pipeline.data.model.PipeStream;
import io.pipeline.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * REST endpoints for testing PipeStreamEngine routing components.
 * Exposes internal routing logic for integration testing.
 */
@Path("/api/test/routing")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PipeStreamTestResource {

    private static final Logger LOG = Logger.getLogger(PipeStreamTestResource.class);

    @Inject
    PipelineConfigService pipelineConfigService;

    @Inject
    DynamicGrpcClientFactory grpcClientFactory;

    /**
     * Test endpoint to extract cluster and pipeline context from a PipeStream.
     */
    @POST
    @Path("/extract-context")
    public Uni<Response> extractContext(PipeStream stream) {
        LOG.infof("Testing context extraction for stream %s", stream.getStreamId());

        return extractClusterAndPipeline(stream)
                .map(context -> Response.ok(Map.of(
                        "success", true,
                        "clusterName", context.clusterName(),
                        "pipelineName", context.pipelineName(),
                        "streamId", stream.getStreamId()
                )).build())
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Context extraction failed for stream %s", stream.getStreamId());
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "success", false,
                                    "error", error.getMessage(),
                                    "streamId", stream.getStreamId()
                            ))
                            .build();
                });
    }

    /**
     * Test endpoint to get step configuration.
     */
    @GET
    @Path("/step-config/{clusterName}/{pipelineName}/{stepName}")
    public Uni<Response> getStepConfig(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineName") String pipelineName,
            @PathParam("stepName") String stepName) {

        LOG.infof("Testing step config retrieval: %s/%s/%s", clusterName, pipelineName, stepName);

        return pipelineConfigService.getPipeStep(clusterName, pipelineName, stepName)
                .map(stepOpt -> {
                    if (stepOpt.isEmpty()) {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(Map.of(
                                        "success", false,
                                        "error", "Step not found: " + stepName,
                                        "clusterName", clusterName,
                                        "pipelineName", pipelineName,
                                        "stepName", stepName
                                ))
                                .build();
                    }

                    PipelineStepConfig stepConfig = stepOpt.get();
                    return Response.ok(Map.of(
                            "success", true,
                            "stepConfig", stepConfig,
                            "clusterName", clusterName,
                            "pipelineName", pipelineName,
                            "stepName", stepName,
                            "serviceName", stepConfig.processorInfo().grpcServiceName()
                    )).build();
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Step config retrieval failed: %s/%s/%s", clusterName, pipelineName, stepName);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    "success", false,
                                    "error", error.getMessage(),
                                    "clusterName", clusterName,
                                    "pipelineName", pipelineName,
                                    "stepName", stepName
                            ))
                            .build();
                });
    }

    /**
     * Test endpoint to check if a gRPC service is available.
     */
    @GET
    @Path("/service-availability/{serviceName}")
    public Uni<Response> checkServiceAvailability(@PathParam("serviceName") String serviceName) {
        LOG.infof("Testing service availability: %s", serviceName);

        return grpcClientFactory.getMutinyClientForService(serviceName)
                .map(client -> Response.ok(Map.of(
                        "success", true,
                        "serviceName", serviceName,
                        "available", true,
                        "message", "Service client created successfully"
                )).build())
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Service availability check failed: %s", serviceName);
                    return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                            .entity(Map.of(
                                    "success", false,
                                    "serviceName", serviceName,
                                    "available", false,
                                    "error", error.getMessage()
                            ))
                            .build();
                });
    }

    /**
     * Test endpoint for complete routing validation.
     */
    @POST
    @Path("/validate-routing")
    public Uni<Response> validateRouting(PipeStream stream) {
        LOG.infof("Testing complete routing validation for stream %s", stream.getStreamId());

        return extractClusterAndPipeline(stream)
                .flatMap(context -> {
                    String targetStep = stream.getTargetStepName();
                    if (targetStep == null || targetStep.isBlank()) {
                        return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                                .entity(Map.of(
                                        "success", false,
                                        "error", "Target step name not specified",
                                        "streamId", stream.getStreamId()
                                ))
                                .build());
                    }

                    return pipelineConfigService.getPipeStep(context.clusterName(), context.pipelineName(), targetStep)
                            .flatMap(stepOpt -> {
                                if (stepOpt.isEmpty()) {
                                    return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND)
                                            .entity(Map.of(
                                                    "success", false,
                                                    "error", "Step not found: " + targetStep,
                                                    "context", context
                                            ))
                                            .build());
                                }

                                PipelineStepConfig stepConfig = stepOpt.get();
                                String serviceName = stepConfig.processorInfo().grpcServiceName();

                                if (serviceName == null || serviceName.isBlank()) {
                                    return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                                            .entity(Map.of(
                                                    "success", false,
                                                    "error", "No gRPC service configured for step",
                                                    "stepName", targetStep,
                                                    "context", context
                                            ))
                                            .build());
                                }

                                return grpcClientFactory.getMutinyClientForService(serviceName)
                                        .map(client -> Response.ok(Map.of(
                                                "success", true,
                                                "message", "Routing validation successful",
                                                "context", context,
                                                "stepConfig", stepConfig,
                                                "serviceName", serviceName,
                                                "serviceAvailable", true
                                        )).build())
                                        .onFailure().recoverWithItem(serviceError -> 
                                                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                                                        .entity(Map.of(
                                                                "success", false,
                                                                "error", "Service unavailable: " + serviceError.getMessage(),
                                                                "context", context,
                                                                "stepConfig", stepConfig,
                                                                "serviceName", serviceName,
                                                                "serviceAvailable", false
                                                        ))
                                                        .build());
                            });
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Complete routing validation failed for stream %s", stream.getStreamId());
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    "success", false,
                                    "error", error.getMessage(),
                                    "streamId", stream.getStreamId()
                            ))
                            .build();
                });
    }

    /**
     * Extract cluster and pipeline context from stream metadata.
     * No defaults - all values must be present in the stream.
     */
    private Uni<PipelineContext> extractClusterAndPipeline(PipeStream stream) {
        LOG.debugf("Extracting context from stream %s", stream.getStreamId());
        
        String pipelineName = stream.getCurrentPipelineName();
        if (pipelineName == null || pipelineName.isBlank()) {
            LOG.errorf("Pipeline name missing in stream %s", stream.getStreamId());
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Pipeline name not specified in stream"));
        }
        
        // Extract cluster name from context params
        String clusterName = stream.getContextParamsMap().get("cluster");
        if (clusterName == null || clusterName.isBlank()) {
            LOG.errorf("Cluster name missing in context params for stream %s", stream.getStreamId());
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Cluster name not specified in stream context params"));
        }
        
        LOG.debugf("Extracted context - cluster: %s, pipeline: %s for stream %s", 
                  clusterName, pipelineName, stream.getStreamId());
        
        return Uni.createFrom().item(new PipelineContext(clusterName, pipelineName));
    }

    /**
     * Create a simple test pipeline with working services
     */
    @POST
    @Path("/create-test-pipeline")
    public Uni<Response> createTestPipeline() {
        LOG.info("Creating simple test pipeline");

        // Create basic pipeline config with parser -> chunker -> embedder
        PipelineConfig config = new PipelineConfig(
                "test-pipeline",
                Map.of(
                        "parse-docs", createStepWithOutput("parse-docs", StepType.PIPELINE, "parser", "chunk-text"),
                        "chunk-text", createStepWithOutput("chunk-text", StepType.PIPELINE, "chunker", "embed-chunks"), 
                        "embed-chunks", createStep("embed-chunks", StepType.SINK, "embedder")
                )
        );

        return pipelineConfigService.createPipeline("dev", "test-pipeline", config)
                .map(result -> {
                    if (result.valid()) {
                        return Response.ok(Map.of(
                                "success", true,
                                "message", "Test pipeline created successfully",
                                "clusterName", "dev",
                                "pipelineId", "test-pipeline"
                        )).build();
                    } else {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(Map.of(
                                        "success", false,
                                        "errors", result.errors(),
                                        "warnings", result.warnings()
                                ))
                                .build();
                    }
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Failed to create test pipeline");
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    "success", false,
                                    "error", error.getMessage()
                            ))
                            .build();
                });
    }

    private PipelineStepConfig createStep(String name, StepType type, String serviceName) {
        return new PipelineStepConfig(
                name,
                type,
                null, // description  
                null, // customConfigSchemaId - will use default from service schema
                new PipelineStepConfig.JsonConfigOptions(Map.of()), // empty custom config
                null, // kafkaInputs
                Map.of(), // outputs
                null, // maxRetries
                null, // retryBackoffMs
                null, // maxRetryBackoffMs
                null, // retryBackoffMultiplier
                null, // stepTimeoutMs
                new PipelineStepConfig.ProcessorInfo(serviceName)
        );
    }

    private PipelineStepConfig createStepWithOutput(String name, StepType type, String serviceName, String outputTarget) {
        return new PipelineStepConfig(
                name,
                type,
                null, // description  
                null, // customConfigSchemaId - will use default from service schema
                new PipelineStepConfig.JsonConfigOptions(Map.of()), // empty custom config
                null, // kafkaInputs
                Map.of("default", new PipelineStepConfig.OutputTarget(
                        outputTarget,
                        TransportType.GRPC,
                        new GrpcTransportConfig(outputTarget, Map.of()), // outputTarget is the target step name  
                        null
                )), // outputs
                null, // maxRetries
                null, // retryBackoffMs
                null, // maxRetryBackoffMs
                null, // retryBackoffMultiplier
                null, // stepTimeoutMs
                new PipelineStepConfig.ProcessorInfo(serviceName)
        );
    }

    /**
     * Simple context record for pipeline execution.
     */
    private record PipelineContext(String clusterName, String pipelineName) {}
}