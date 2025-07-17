package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ModuleRegistration;
import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ServiceHealthStatus;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;

/**
 * REST resource for Global Module Registry operations.
 * Provides OpenAPI endpoints for module registration, discovery, and management.
 */
@Path("/api/v1/modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Global Module Registry", description = "Operations for managing globally registered modules")
public class GlobalModuleRegistryResource {
    
    private static final Logger LOG = Logger.getLogger(GlobalModuleRegistryResource.class);
    
    @Inject
    GlobalModuleRegistryService moduleRegistry;
    
    @POST
    @Path("/register")
    @Operation(summary = "Register a new module", description = "Registers a module in the global registry")
    @APIResponse(responseCode = "201", description = "Module registered successfully")
    @APIResponse(responseCode = "409", description = "Module already exists at this endpoint")
    public Uni<Response> registerModule(ModuleRegistrationRequest request) {
        LOG.debugf("Received registration request: moduleName=%s, implementationId=%s, host=%s, port=%d", 
            request.getModuleName(), request.getImplementationId(), request.getHost(), request.getPort());
        
        return moduleRegistry.registerModule(
                request.getModuleName(),
                request.getImplementationId(),
                request.getHost(),
                request.getPort(),
                request.getServiceType(),
                request.getVersion(),
                request.getMetadata(),
                request.getEngineHost(),
                request.getEnginePort(),
                request.getJsonSchema()
            )
            .onItem().transform(registration -> 
                Response.status(Response.Status.CREATED).entity(registration).build()
            )
            .onFailure().recoverWithItem(throwable -> {
                if (throwable.getMessage().contains("already exists")) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", throwable.getMessage()))
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", throwable.getMessage()))
                    .build();
            });
    }
    
    @GET
    @Operation(summary = "List all registered modules", description = "Returns all modules in the global registry")
    @APIResponse(responseCode = "200", description = "List of registered modules")
    public Uni<Set<ModuleRegistration>> listModules() {
        return moduleRegistry.listRegisteredModules();
    }
    
    @GET
    @Path("/enabled")
    @Operation(summary = "List enabled modules", description = "Returns only enabled modules")
    @APIResponse(responseCode = "200", description = "List of enabled modules")
    public Uni<Set<ModuleRegistration>> listEnabledModules() {
        return moduleRegistry.listEnabledModules();
    }
    
    @GET
    @Path("/{moduleId}")
    @Operation(summary = "Get module by ID", description = "Returns a specific module by its ID")
    @APIResponse(responseCode = "200", description = "Module found")
    @APIResponse(responseCode = "404", description = "Module not found")
    public Uni<Response> getModule(
            @Parameter(description = "Module ID", required = true)
            @PathParam("moduleId") String moduleId) {
        return moduleRegistry.getModule(moduleId)
            .onItem().transform(module -> {
                if (module == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Module not found: " + moduleId))
                        .build();
                }
                return Response.ok(module).build();
            });
    }
    
    @PUT
    @Path("/{moduleId}/disable")
    @Operation(summary = "Disable a module", description = "Disables a module without removing it")
    @APIResponse(responseCode = "200", description = "Module disabled successfully")
    @APIResponse(responseCode = "404", description = "Module not found")
    public Uni<Response> disableModule(
            @Parameter(description = "Module ID", required = true)
            @PathParam("moduleId") String moduleId) {
        return moduleRegistry.disableModule(moduleId)
            .onItem().transform(success -> {
                if (success) {
                    return Response.ok(Map.of("status", "disabled")).build();
                }
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Module not found: " + moduleId))
                    .build();
            });
    }
    
    @PUT
    @Path("/{moduleId}/enable")
    @Operation(summary = "Enable a module", description = "Enables a previously disabled module")
    @APIResponse(responseCode = "200", description = "Module enabled successfully")
    @APIResponse(responseCode = "404", description = "Module not found")
    public Uni<Response> enableModule(
            @Parameter(description = "Module ID", required = true)
            @PathParam("moduleId") String moduleId) {
        return moduleRegistry.enableModule(moduleId)
            .onItem().transform(success -> {
                if (success) {
                    return Response.ok(Map.of("status", "enabled")).build();
                }
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Module not found: " + moduleId))
                    .build();
            });
    }
    
    @DELETE
    @Path("/{moduleId}")
    @Operation(summary = "Deregister a module", description = "Permanently removes a module from the registry")
    @APIResponse(responseCode = "204", description = "Module deregistered successfully")
    public Uni<Response> deregisterModule(
            @Parameter(description = "Module ID", required = true)
            @PathParam("moduleId") String moduleId) {
        return moduleRegistry.deregisterModule(moduleId)
            .onItem().transform(v -> Response.noContent().build());
    }
    
    @GET
    @Path("/{moduleId}/health")
    @Operation(summary = "Get module health status", description = "Returns the health status of a module")
    @APIResponse(responseCode = "200", description = "Health status retrieved")
    public Uni<ServiceHealthStatus> getModuleHealth(
            @Parameter(description = "Module ID", required = true)
            @PathParam("moduleId") String moduleId) {
        return moduleRegistry.getModuleHealthStatus(moduleId);
    }
    
    /**
     * Request object for module registration
     */
    public static class ModuleRegistrationRequest {
        private String moduleName;
        private String implementationId;
        private String host;
        private int port;
        private String serviceType;
        private String version;
        private Map<String, String> metadata;
        private String engineHost;
        private int enginePort;
        private String jsonSchema;
        
        public ModuleRegistrationRequest() {}
        
        public ModuleRegistrationRequest(String moduleName, String implementationId, String host, 
                int port, String serviceType, String version, Map<String, String> metadata, 
                String engineHost, int enginePort, String jsonSchema) {
            this.moduleName = moduleName;
            this.implementationId = implementationId;
            this.host = host;
            this.port = port;
            this.serviceType = serviceType;
            this.version = version;
            this.metadata = metadata;
            this.engineHost = engineHost;
            this.enginePort = enginePort;
            this.jsonSchema = jsonSchema;
        }
        
        // Getters - using standard JavaBean naming
        public String getModuleName() { return moduleName; }
        public String getImplementationId() { return implementationId; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getServiceType() { return serviceType; }
        public String getVersion() { return version; }
        public Map<String, String> getMetadata() { return metadata; }
        public String getEngineHost() { return engineHost; }
        public int getEnginePort() { return enginePort; }
        public String getJsonSchema() { return jsonSchema; }
        
        // Setters for Jackson
        public void setModuleName(String moduleName) { this.moduleName = moduleName; }
        public void setImplementationId(String implementationId) { this.implementationId = implementationId; }
        public void setHost(String host) { this.host = host; }
        public void setPort(int port) { this.port = port; }
        public void setServiceType(String serviceType) { this.serviceType = serviceType; }
        public void setVersion(String version) { this.version = version; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
        public void setEngineHost(String engineHost) { this.engineHost = engineHost; }
        public void setEnginePort(int enginePort) { this.enginePort = enginePort; }
        public void setJsonSchema(String jsonSchema) { this.jsonSchema = jsonSchema; }
    }
}