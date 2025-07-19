package io.pipeline.consul.client.api;

import io.pipeline.api.model.PipelineInstance;
import io.pipeline.api.model.CreateInstanceRequest;
import io.pipeline.api.service.PipelineInstanceService;
import io.pipeline.api.validation.ValidationResult;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST resource for Pipeline Instance operations.
 * Provides OpenAPI endpoints for managing pipeline instances within clusters.
 */
@Path("/api/v1/clusters/{clusterName}/instances")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Pipeline Instances", description = "Operations for managing pipeline instances in clusters")
public class PipelineInstanceResource {
    
    @Inject
    PipelineInstanceService pipelineInstanceService;
    
    @GET
    @Operation(summary = "List pipeline instances", description = "Lists all pipeline instances in a cluster")
    @APIResponse(responseCode = "200", description = "List of pipeline instances")
    public Uni<List<PipelineInstance>> listPipelineInstances(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName) {
        return pipelineInstanceService.listInstances(clusterName);
    }
    
    @GET
    @Path("/{instanceId}")
    @Operation(summary = "Get pipeline instance", description = "Gets a specific pipeline instance")
    @APIResponse(responseCode = "200", description = "Pipeline instance found")
    @APIResponse(responseCode = "404", description = "Pipeline instance not found")
    public Uni<Response> getPipelineInstance(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,
            @Parameter(description = "Instance ID", required = true)
            @PathParam("instanceId") String instanceId) {
        return pipelineInstanceService.getInstance(clusterName, instanceId)
            .onItem().transform(instance -> {
                if (instance == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.ok(instance).build();
            });
    }
    
    @POST
    @Path("/deploy")
    @Operation(summary = "Deploy new pipeline instance", description = "Creates and deploys a new pipeline instance from a definition")
    @APIResponse(responseCode = "201", description = "Pipeline instance deployed successfully")
    @APIResponse(responseCode = "400", description = "Validation failed")
    public Uni<Response> deployPipelineInstance(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,
            CreateInstanceRequest request) {
        return pipelineInstanceService.createInstance(clusterName, request)
            .onItem().transform(result -> {
                if (result.valid()) {
                    return Response.status(Response.Status.CREATED).entity(result).build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
            });
    }
    
    @PUT
    @Path("/{instanceId}")
    @Operation(summary = "Update pipeline instance", description = "Updates an existing pipeline instance configuration")
    @APIResponse(responseCode = "200", description = "Pipeline instance updated successfully")
    @APIResponse(responseCode = "400", description = "Validation failed")
    public Uni<Response> updatePipelineInstance(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,
            @Parameter(description = "Instance ID", required = true)
            @PathParam("instanceId") String instanceId,
            PipelineInstance instance) {
        return pipelineInstanceService.updateInstance(clusterName, instanceId, instance)
            .onItem().transform(result -> {
                if (result.valid()) {
                    return Response.ok(result).build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
            });
    }
    
    @DELETE
    @Path("/{instanceId}")
    @Operation(summary = "Delete pipeline instance", description = "Deletes a pipeline instance (must be stopped first)")
    @APIResponse(responseCode = "204", description = "Pipeline instance deleted successfully")
    @APIResponse(responseCode = "400", description = "Instance must be stopped before deletion")
    public Uni<Response> deletePipelineInstance(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,
            @Parameter(description = "Instance ID", required = true)
            @PathParam("instanceId") String instanceId) {
        return pipelineInstanceService.deleteInstance(clusterName, instanceId)
            .onItem().transform(result -> {
                if (result.valid()) {
                    return Response.noContent().build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
            });
    }
    
    @POST
    @Path("/{instanceId}/start")
    @Operation(summary = "Start pipeline instance", description = "Starts a stopped pipeline instance")
    @APIResponse(responseCode = "200", description = "Pipeline instance started successfully")
    @APIResponse(responseCode = "400", description = "Failed to start instance")
    public Uni<Response> startPipelineInstance(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,
            @Parameter(description = "Instance ID", required = true)
            @PathParam("instanceId") String instanceId) {
        return pipelineInstanceService.startInstance(clusterName, instanceId)
            .onItem().transform(result -> {
                if (result.valid()) {
                    return Response.ok(result).build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
            });
    }
    
    @POST
    @Path("/{instanceId}/stop")
    @Operation(summary = "Stop pipeline instance", description = "Stops a running pipeline instance")
    @APIResponse(responseCode = "200", description = "Pipeline instance stopped successfully")
    @APIResponse(responseCode = "400", description = "Failed to stop instance")
    public Uni<Response> stopPipelineInstance(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,
            @Parameter(description = "Instance ID", required = true)
            @PathParam("instanceId") String instanceId) {
        return pipelineInstanceService.stopInstance(clusterName, instanceId)
            .onItem().transform(result -> {
                if (result.valid()) {
                    return Response.ok(result).build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
            });
    }
    
    @GET
    @Path("/{instanceId}/exists")
    @Operation(summary = "Check instance exists", description = "Checks if a pipeline instance exists")
    @APIResponse(responseCode = "200", description = "Existence check result")
    public Uni<Response> checkInstanceExists(
            @Parameter(description = "Cluster name", required = true)
            @PathParam("clusterName") String clusterName,
            @Parameter(description = "Instance ID", required = true)
            @PathParam("instanceId") String instanceId) {
        return pipelineInstanceService.instanceExists(clusterName, instanceId)
            .onItem().transform(exists -> Response.ok(new ExistenceCheckResult(exists)).build());
    }
    
    /**
     * Additional endpoint to list instances by pipeline definition
     */
    @GET
    @Path("/by-definition/{pipelineDefinitionId}")
    @Operation(summary = "List instances by definition", description = "Lists all instances of a specific pipeline definition across all clusters")
    @APIResponse(responseCode = "200", description = "List of pipeline instances")
    public Uni<List<PipelineInstance>> listInstancesByPipelineDefinition(
            @Parameter(description = "Pipeline definition ID", required = true)
            @PathParam("pipelineDefinitionId") String pipelineDefinitionId) {
        return pipelineInstanceService.listInstancesByDefinition(pipelineDefinitionId);
    }
    
    /**
     * Response object for existence checks
     */
    public record ExistenceCheckResult(boolean exists) {}
}