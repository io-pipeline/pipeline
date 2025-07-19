package io.pipeline.consul.client.api;

import io.pipeline.api.model.ModuleWhitelistRequest;
import io.pipeline.api.model.ModuleWhitelistResponse;
import io.pipeline.api.service.ModuleWhitelistService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * REST resource for Module Whitelist operations.
 * Provides OpenAPI endpoints for managing module whitelists in clusters.
 */
@Path("/api/v1/clusters/{clusterName}/whitelist")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Module Whitelist", description = "Operations for managing module whitelists in clusters")
public class ModuleWhitelistResource {
    
    private static final Logger LOG = Logger.getLogger(ModuleWhitelistResource.class);
    
    @Inject
    ModuleWhitelistService moduleWhitelistService;
    
    @POST
    @Operation(summary = "Add module to whitelist", description = "Adds a module to the cluster's whitelist")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Module added to whitelist successfully"),
        @APIResponse(responseCode = "400", description = "Invalid request or module not found"),
        @APIResponse(responseCode = "404", description = "Cluster not found")
    })
    public Uni<Response> whitelistModule(
            @Parameter(description = "The cluster name") @PathParam("clusterName") String clusterName,
            ModuleWhitelistRequest request) {
        
        LOG.infof("REST API: Adding module '%s' to whitelist for cluster '%s'", 
                 request.grpcServiceName(), clusterName);
        
        return moduleWhitelistService.whitelistModule(clusterName, request)
            .map(response -> {
                if (response.success()) {
                    return Response.ok(response).build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(response)
                        .build();
                }
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to whitelist module '%s' for cluster '%s'", 
                          request.grpcServiceName(), clusterName);
                ModuleWhitelistResponse errorResponse = ModuleWhitelistResponse.failure(
                    "Failed to whitelist module: " + throwable.getMessage()
                );
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .build();
            });
    }
    
    @GET
    @Operation(summary = "List whitelisted modules", description = "Lists all modules in the cluster's whitelist")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Whitelist retrieved successfully"),
        @APIResponse(responseCode = "404", description = "Cluster not found")
    })
    public Uni<Response> listWhitelistedModules(
            @Parameter(description = "The cluster name") @PathParam("clusterName") String clusterName) {
        
        LOG.infof("REST API: Listing whitelisted modules for cluster '%s'", clusterName);
        
        return moduleWhitelistService.listWhitelistedModules(clusterName)
            .map(modules -> Response.ok(modules).build())
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to list whitelisted modules for cluster '%s'", clusterName);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to retrieve whitelist: " + throwable.getMessage())
                    .build();
            });
    }
    
    @DELETE
    @Path("/{serviceId}")
    @Operation(summary = "Remove module from whitelist", description = "Removes a module from the cluster's whitelist")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Module removed from whitelist successfully"),
        @APIResponse(responseCode = "400", description = "Cannot remove module or module not found"),
        @APIResponse(responseCode = "404", description = "Cluster not found")
    })
    public Uni<Response> removeModuleFromWhitelist(
            @Parameter(description = "The cluster name") @PathParam("clusterName") String clusterName,
            @Parameter(description = "The service ID to remove") @PathParam("serviceId") String serviceId) {
        
        LOG.infof("REST API: Removing module '%s' from whitelist for cluster '%s'", serviceId, clusterName);
        
        return moduleWhitelistService.removeModuleFromWhitelist(clusterName, serviceId)
            .map(response -> {
                if (response.success()) {
                    return Response.ok(response).build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(response)
                        .build();
                }
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to remove module '%s' from whitelist for cluster '%s'", 
                          serviceId, clusterName);
                ModuleWhitelistResponse errorResponse = ModuleWhitelistResponse.failure(
                    "Failed to remove module from whitelist: " + throwable.getMessage()
                );
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .build();
            });
    }
}