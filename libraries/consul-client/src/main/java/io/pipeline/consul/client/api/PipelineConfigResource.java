package io.pipeline.consul.client.api;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.service.PipelineConfigService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Test implementation of PipelineConfigResource for the consul module.
 * This class is used for testing the PipelineConfigService via REST API.
 */
@Path("/api/v1/clusters/{clusterName}/pipelines")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Pipeline Configuration", description = "CRUD operations for pipeline configurations in Consul")
public class PipelineConfigResource {

    private static final Logger LOG = Logger.getLogger(PipelineConfigResource.class);

    @Inject
    PipelineConfigService pipelineConfigService;

    @POST
    @Path("/{pipelineId}")
    @Operation(
        summary = "Create a new pipeline",
        description = "Creates a new pipeline configuration in Consul with validation"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Pipeline created successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "409",
            description = "Pipeline already exists",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiResponse.class)
            )
        )
    })
    public Uni<Response> createPipeline(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,

            @Parameter(description = "Pipeline ID", required = true)
            @PathParam("pipelineId") String pipelineId,

            @Parameter(description = "Pipeline configuration", required = true)
            PipelineConfig config) {

        LOG.infof("Creating pipeline '%s' in cluster '%s'", pipelineId, clusterName);

        return pipelineConfigService.createPipeline(clusterName, pipelineId, config)
            .map(result -> {
            if (result.valid()) {
                return Response.status(Status.CREATED)
                        .entity(new ApiResponse(true, "Pipeline created successfully", null, null))
                        .build();
            } else {
                Status status = result.errors().stream()
                        .anyMatch(e -> e.contains("already exists")) 
                        ? Status.CONFLICT : Status.BAD_REQUEST;

                return Response.status(status)
                        .entity(new ApiResponse(false, "Validation failed", result.errors(), result.warnings()))
                        .build();
            }
        });
    }

    @PUT
    @Path("/{pipelineId}")
    @Operation(
        summary = "Update a pipeline",
        description = "Updates an existing pipeline configuration in Consul"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline updated successfully"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation failed"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline not found"
        )
    })
    public Uni<Response> updatePipeline(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineId") String pipelineId,
            PipelineConfig config) {

        LOG.infof("Updating pipeline '%s' in cluster '%s'", pipelineId, clusterName);

        return pipelineConfigService.updatePipeline(clusterName, pipelineId, config)
            .map(result -> {
            if (result.valid()) {
                return Response.ok(new ApiResponse(true, "Pipeline updated successfully", null, null))
                        .build();
            } else {
                Status status = result.errors().stream()
                        .anyMatch(e -> e.contains("not found")) 
                        ? Status.NOT_FOUND : Status.BAD_REQUEST;

                return Response.status(status)
                        .entity(new ApiResponse(false, "Update failed", result.errors(), result.warnings()))
                        .build();
            }
        });
    }

    @DELETE
    @Path("/{pipelineId}")
    @Operation(
        summary = "Delete a pipeline",
        description = "Deletes a pipeline configuration from Consul"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "204",
            description = "Pipeline deleted successfully"
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline not found"
        )
    })
    public Uni<Response> deletePipeline(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineId") String pipelineId) {

        LOG.infof("Deleting pipeline '%s' from cluster '%s'", pipelineId, clusterName);

        return pipelineConfigService.deletePipeline(clusterName, pipelineId)
            .map(result -> {
            if (result.valid()) {
                return Response.noContent().build();
            } else {
                return Response.status(Status.NOT_FOUND)
                        .entity(new ApiResponse(false, "Delete failed", result.errors(), null))
                        .build();
            }
        });
    }

    @GET
    @Path("/{pipelineId}")
    @Operation(
        summary = "Get a pipeline",
        description = "Retrieves a pipeline configuration from Consul"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = PipelineConfig.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Pipeline not found"
        )
    })
    public Uni<Response> getPipeline(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineId") String pipelineId) {

        LOG.debugf("Getting pipeline '%s' from cluster '%s'", pipelineId, clusterName);

        return pipelineConfigService.getPipeline(clusterName, pipelineId)
            .map(optional -> {
            if (optional.isPresent()) {
                return Response.ok(optional.get()).build();
            } else {
                return Response.status(Status.NOT_FOUND)
                        .entity(new ApiResponse(false, "Pipeline not found", 
                                java.util.List.of("Pipeline '" + pipelineId + "' not found"), null))
                        .build();
            }
        });
    }

    @GET
    @Operation(
        summary = "List all pipelines",
        description = "Lists all pipeline configurations in a cluster"
    )
    @APIResponse(
        responseCode = "200",
        description = "List of pipelines",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.OBJECT,
                implementation = Map.class
            )
        )
    )
    public Uni<Map<String, PipelineConfig>> listPipelines(
            @PathParam("clusterName") String clusterName) {

        LOG.debugf("Listing pipelines in cluster '%s'", clusterName);

        return pipelineConfigService.listPipelines(clusterName);
    }

    // Convenience endpoints for pipeline step access
    
    @GET
    @Path("/{pipelineId}/steps/{stepId}")
    @Operation(
        summary = "Get a specific step from a pipeline",
        description = "Retrieves a specific step configuration from a pipeline"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Step found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = PipelineStepConfig.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Step or pipeline not found"
        )
    })
    public Uni<Response> getPipeStep(
            @PathParam("clusterName") String clusterName,
            @PathParam("pipelineId") String pipelineId,
            @PathParam("stepId") String stepId) {

        LOG.debugf("Getting step '%s' from pipeline '%s' in cluster '%s'", stepId, pipelineId, clusterName);

        return pipelineConfigService.getPipeStep(clusterName, pipelineId, stepId)
            .map(optional -> {
                if (optional.isPresent()) {
                    return Response.ok(optional.get()).build();
                } else {
                    return Response.status(Status.NOT_FOUND)
                            .entity(new ApiResponse(false, "Step not found", 
                                    java.util.List.of("Step '" + stepId + "' not found in pipeline '" + pipelineId + "'"), null))
                            .build();
                }
            });
    }

    @GET
    @Path("/steps/{stepId}")
    @Operation(
        summary = "Find a step across all pipelines in cluster",
        description = "Searches all pipelines in a cluster for a step with the given ID"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Step found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = PipelineStepConfig.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Step not found in any pipeline"
        )
    })
    public Uni<Response> findPipeStep(
            @PathParam("clusterName") String clusterName,
            @PathParam("stepId") String stepId) {

        LOG.debugf("Searching for step '%s' across all pipelines in cluster '%s'", stepId, clusterName);

        return pipelineConfigService.findPipeStep(clusterName, stepId)
            .map(optional -> {
                if (optional.isPresent()) {
                    return Response.ok(optional.get()).build();
                } else {
                    return Response.status(Status.NOT_FOUND)
                            .entity(new ApiResponse(false, "Step not found", 
                                    java.util.List.of("Step '" + stepId + "' not found in any pipeline in cluster '" + clusterName + "'"), null))
                            .build();
                }
            });
    }

    @GET
    @Path("/steps/{stepId}/pipeline")
    @Operation(
        summary = "Find which pipeline contains a step",
        description = "Returns the pipeline ID that contains the specified step"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Pipeline found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = String.class)
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Step not found in any pipeline"
        )
    })
    public Uni<Response> getPipelineForStep(
            @PathParam("clusterName") String clusterName,
            @PathParam("stepId") String stepId) {

        LOG.debugf("Finding which pipeline contains step '%s' in cluster '%s'", stepId, clusterName);

        return pipelineConfigService.getPipelineForStep(clusterName, stepId)
            .map(optional -> {
                if (optional.isPresent()) {
                    return Response.ok(java.util.Map.of(
                        "stepId", stepId,
                        "pipelineId", optional.get(),
                        "clusterName", clusterName
                    )).build();
                } else {
                    return Response.status(Status.NOT_FOUND)
                            .entity(new ApiResponse(false, "Step not found", 
                                    java.util.List.of("Step '" + stepId + "' not found in any pipeline in cluster '" + clusterName + "'"), null))
                            .build();
                }
            });
    }

    /**
     * API response wrapper
     */
    public record ApiResponse(
            boolean success,
            String message,
            java.util.List<String> errors,
            java.util.List<String> warnings
    ) {}
}
