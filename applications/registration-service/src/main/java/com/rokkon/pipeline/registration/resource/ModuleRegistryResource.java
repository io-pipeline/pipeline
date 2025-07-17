package com.rokkon.pipeline.registration.resource;

import com.rokkon.pipeline.commons.model.GlobalModuleRegistryService;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/modules")
@Produces(MediaType.APPLICATION_JSON)
public class ModuleRegistryResource {
    
    private static final Logger log = Logger.getLogger(ModuleRegistryResource.class);
    
    @Inject
    GlobalModuleRegistryService globalModuleRegistryService;
    
    @Inject
    ConsulClient consulClient;
    
    @GET
    public Uni<List<Map<String, Object>>> listModules() {
        return globalModuleRegistryService.listRegisteredModules()
            .map(modules -> modules.stream()
                .map(module -> Map.<String, Object>of(
                    "moduleId", module.moduleId(),
                    "moduleName", module.moduleName(),
                    "host", module.host(),
                    "port", module.port(),
                    "serviceType", module.serviceType(),
                    "version", module.version(),
                    "enabled", module.enabled(),
                    "metadata", module.metadata(),
                    "registeredAt", module.registeredAt()
                ))
                .collect(Collectors.toList())
            );
    }
    
    @GET
    @Path("/health-status")
    public Uni<List<Map<String, Object>>> getModuleHealthStatus() {
        return globalModuleRegistryService.listRegisteredModules()
            .onItem().transformToUni(modules -> {
                List<Uni<Map<String, Object>>> healthChecks = modules.stream()
                    .map(module -> 
                        globalModuleRegistryService.getModuleHealthStatus(module.moduleId())
                            .map(health -> Map.<String, Object>of(
                                "moduleId", module.moduleId(),
                                "moduleName", module.moduleName(),
                                "healthStatus", health.healthStatus().name(),
                                "isHealthy", health.healthStatus() == GlobalModuleRegistryService.HealthStatus.PASSING,
                                "exists", health.exists()
                            ))
                            .onFailure().recoverWithItem(Map.<String, Object>of(
                                "moduleId", module.moduleId(),
                                "moduleName", module.moduleName(),
                                "healthStatus", "UNKNOWN",
                                "isHealthy", false,
                                "exists", false,
                                "error", "Failed to check health"
                            ))
                    )
                    .toList();
                
                return Uni.combine().all().unis(healthChecks)
                    .with(results -> results.stream()
                        .map(r -> (Map<String, Object>) r)
                        .collect(Collectors.toList())
                    );
            });
    }
    
    @GET
    @Path("/consul-services")
    public Uni<Map<String, Object>> getConsulServices() {
        return consulClient.catalogServices()
            .map(serviceList -> {
                Map<String, Object> services = serviceList.getList().stream()
                    .collect(Collectors.toMap(
                        service -> service.getName(),
                        service -> service.getTags()
                    ));
                
                return Map.of(
                    "services", services,
                    "totalCount", services.size()
                );
            })
            .onFailure().recoverWithItem(t -> {
                log.error("Failed to fetch Consul services", t);
                return Map.of(
                    "error", "Failed to fetch services from Consul",
                    "message", t.getMessage()
                );
            });
    }
    
    @GET
    @Path("/service-metadata")
    public Uni<List<Map<String, Object>>> getServiceMetadata() {
        return consulClient.catalogServices()
            .onItem().transformToUni(serviceList -> {
                List<Uni<Map<String, Object>>> metadataFutures = serviceList.getList().stream()
                    .map(service -> 
                        consulClient.healthServiceNodes(service.getName(), false)
                            .map(healthNodes -> {
                                if (healthNodes.getList() != null && !healthNodes.getList().isEmpty()) {
                                    var node = healthNodes.getList().get(0);
                                    return Map.<String, Object>of(
                                        "serviceName", service.getName(),
                                        "serviceId", node.getService().getId(),
                                        "address", node.getService().getAddress(),
                                        "port", node.getService().getPort(),
                                        "tags", service.getTags(),
                                        "meta", node.getService().getMeta(),
                                        "healthStatus", node.getChecks().stream()
                                            .map(check -> Map.of(
                                                "name", check.getName(),
                                                "status", check.getStatus().name()
                                            ))
                                            .collect(Collectors.toList())
                                    );
                                } else {
                                    return Map.<String, Object>of(
                                        "serviceName", service.getName(),
                                        "tags", service.getTags(),
                                        "error", "No health nodes found"
                                    );
                                }
                            })
                            .onFailure().recoverWithItem(t -> Map.<String, Object>of(
                                "serviceName", service.getName(),
                                "error", t.getMessage()
                            ))
                    )
                    .toList();
                
                return Uni.combine().all().unis(metadataFutures)
                    .with(results -> results.stream()
                        .map(r -> (Map<String, Object>) r)
                        .collect(Collectors.toList())
                    );
            });
    }
}