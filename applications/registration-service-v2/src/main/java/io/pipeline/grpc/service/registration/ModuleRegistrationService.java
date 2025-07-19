package io.pipeline.grpc.service.registration;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.pipeline.api.service.ModuleRegistryService;
import io.pipeline.data.module.MutinyPipeStepProcessorGrpc;
import io.pipeline.data.module.RegistrationRequest;
import io.pipeline.registration.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@GrpcService
public class ModuleRegistrationService extends MutinyModuleRegistrationGrpc.ModuleRegistrationImplBase {

    private static final Logger log = Logger.getLogger(ModuleRegistrationService.class);
    
    @Inject
    ModuleRegistryService globalModuleRegistryService;
    
    @Inject
    io.vertx.mutiny.ext.consul.ConsulClient consulClient;

    @Override
    public Uni<RegistrationStatus> registerModule(ModuleInfo request) {
        log.infof("Received registration request for module %s at %s:%d", 
                  request.getServiceName(), request.getHost(), request.getPort());
        
        // Create a dynamic channel to the module
        ManagedChannel channel = io.grpc.ManagedChannelBuilder.forAddress(request.getHost(), request.getPort()).usePlaintext().build();
        var client = MutinyPipeStepProcessorGrpc.newMutinyStub(channel);

        // Call the module to get its registration info
        return client.getServiceRegistration(RegistrationRequest.newBuilder().build())
                .onItem().transformToUni(serviceRegistrationResponse -> {
                    // The GlobalModuleRegistryService already registers with Consul
                    // So we just need to call it to handle everything
                    return globalModuleRegistryService.registerModule(
                            serviceRegistrationResponse.getModuleName(),
                            request.getServiceId(),
                            request.getHost(),
                            request.getPort(),
                            "grpc",
                            serviceRegistrationResponse.getVersion(),
                            serviceRegistrationResponse.getMetadataMap(),
                            request.getHost(), // engineHost - same as module host for now
                            request.getPort(), // enginePort - same as module port for now
                            serviceRegistrationResponse.getJsonConfigSchema()
                    ).map(registration -> RegistrationStatus.newBuilder()
                            .setSuccess(true)
                            .setMessage("Module registered successfully with Consul and registration service")
                            .setConsulServiceId(registration.moduleId())
                            .build());
                })
                .onFailure().recoverWithItem(throwable -> RegistrationStatus.newBuilder()
                        .setSuccess(false)
                        .setMessage(throwable.getMessage())
                        .build());
    }

    @Override
    public Uni<UnregistrationStatus> unregisterModule(ModuleId request) {
        return globalModuleRegistryService.deregisterModule(request.getServiceId())
                .map(v -> UnregistrationStatus.newBuilder().setSuccess(true).build());
    }

    @Override
    public Uni<HeartbeatAck> heartbeat(ModuleHeartbeat request) {
        // Not implemented yet
        return Uni.createFrom().item(HeartbeatAck.newBuilder().setAcknowledged(true).build());
    }

    @Override
    public Uni<ModuleHealthStatus> getModuleHealth(ModuleId request) {
        return globalModuleRegistryService.getModuleHealthStatus(request.getServiceId())
                .map(healthStatus -> ModuleHealthStatus.newBuilder()
                        .setServiceId(healthStatus.module().moduleId())
                        .setServiceName(healthStatus.module().moduleName())
                        .setIsHealthy(healthStatus.healthStatus() == ModuleRegistryService.HealthStatus.PASSING)
                        .build());
    }

    @Override
    public Uni<ModuleList> listModules(Empty request) {
        return globalModuleRegistryService.listRegisteredModules()
                .map(modules -> {
                    ModuleList.Builder moduleListBuilder = ModuleList.newBuilder();
                    modules.forEach(module -> moduleListBuilder.addModules(ModuleInfo.newBuilder()
                            .setServiceName(module.moduleName())
                            .setServiceId(module.moduleId())
                            .setHost(module.host())
                            .setPort(module.port())
                            .putAllMetadata(module.metadata())
                            .build()));
                    return moduleListBuilder.build();
                });
    }
}