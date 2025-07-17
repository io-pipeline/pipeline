package com.pipeline.registration.resource;

import com.google.protobuf.Empty;
import com.pipeline.registration.*;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * REST endpoint to bridge between web UI and gRPC service.
 * This allows testing the gRPC service from a web browser.
 */
@Path("/api/registration")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RegistrationResource {

    @GrpcClient("registration")
    MutinyModuleRegistrationGrpc.MutinyModuleRegistrationStub registrationClient;

    @POST
    @Path("/register")
    public Uni<Response> registerModule(ModuleRegistrationRequest request) {
        ModuleInfo.Builder moduleInfo = ModuleInfo.newBuilder()
                .setServiceName(request.serviceName)
                .setHost(request.host)
                .setPort(request.port);
        
        if (request.serviceId != null && !request.serviceId.isEmpty()) {
            moduleInfo.setServiceId(request.serviceId);
        }
        
        if (request.metadata != null) {
            moduleInfo.putAllMetadata(request.metadata);
        }
        
        return registrationClient.registerModule(moduleInfo.build())
                .map(status -> Response.ok(Map.of(
                        "success", status.getSuccess(),
                        "message", status.getMessage(),
                        "consulServiceId", status.getConsulServiceId()
                )).build())
                .onFailure().recoverWithItem(throwable -> 
                    Response.serverError().entity(Map.of(
                            "success", false,
                            "message", throwable.getMessage()
                    )).build()
                );
    }

    @DELETE
    @Path("/unregister/{serviceId}")
    public Uni<Response> unregisterModule(@PathParam("serviceId") String serviceId) {
        return registrationClient.unregisterModule(
                ModuleId.newBuilder().setServiceId(serviceId).build()
        )
        .map(status -> Response.ok(Map.of(
                "success", status.getSuccess(),
                "message", "Module unregistered successfully"
        )).build())
        .onFailure().recoverWithItem(throwable -> 
            Response.serverError().entity(Map.of(
                    "success", false,
                    "message", throwable.getMessage()
            )).build()
        );
    }

    @GET
    @Path("/health/{serviceId}")
    public Uni<Response> getModuleHealth(@PathParam("serviceId") String serviceId) {
        return registrationClient.getModuleHealth(
                ModuleId.newBuilder().setServiceId(serviceId).build()
        )
        .map(health -> Response.ok(Map.of(
                "serviceId", health.getServiceId(),
                "serviceName", health.getServiceName(),
                "isHealthy", health.getIsHealthy()
        )).build())
        .onFailure().recoverWithItem(throwable -> 
            Response.serverError().entity(Map.of(
                    "error", throwable.getMessage()
            )).build()
        );
    }

    @GET
    @Path("/list")
    public Uni<Response> listModules() {
        return registrationClient.listModules(Empty.getDefaultInstance())
                .map(moduleList -> {
                    var modules = moduleList.getModulesList().stream()
                            .map(module -> Map.of(
                                    "serviceName", module.getServiceName(),
                                    "serviceId", module.getServiceId(),
                                    "host", module.getHost(),
                                    "port", module.getPort(),
                                    "metadata", module.getMetadataMap()
                            ))
                            .toList();
                    return Response.ok(Map.of("modules", modules)).build();
                })
                .onFailure().recoverWithItem(throwable -> 
                    Response.serverError().entity(Map.of(
                            "error", throwable.getMessage()
                    )).build()
                );
    }

    // DTO for registration request
    public static class ModuleRegistrationRequest {
        public String serviceName;
        public String serviceId;
        public String host;
        public int port;
        public Map<String, String> metadata;
    }
}