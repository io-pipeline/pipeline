package io.pipeline.engine;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

/**
 * Registers the PipeStream Engine directly with Consul.
 * Completely separate from all other registration mechanisms.
 */
@ApplicationScoped
public class EngineConsulRegistration {
    
    private static final Logger LOG = Logger.getLogger(EngineConsulRegistration.class);
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "registration-service")
    String applicationName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String applicationVersion;

    @Inject
    ConsulClient consulClient;
    
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "38100")
    int httpPort;
    
    private String serviceId;
    
    void onStart(@Observes StartupEvent ev) {
        LOG.info("EngineConsulRegistration starting");
        
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            serviceId = "pipestream-engine-" + UUID.randomUUID().toString().substring(0, 8);
            
            LOG.infof("Registering pipestream-engine at %s:%d with ID: %s", hostname, httpPort, serviceId);
            
            ServiceOptions serviceOptions = new ServiceOptions()
                .setName("pipestream-engine")
                .setId(serviceId)
                .setAddress(hostname)
                .setPort(httpPort)
                    .setTags(List.of("grpc", applicationName, "core-service", "version:" + applicationVersion));

            CheckOptions checkOptions = new CheckOptions()
                .setName("Engine Health Check")
                .setGrpc(hostname + ":" + httpPort)
                .setInterval("10s")
                .setDeregisterAfter("1m");

            serviceOptions.setCheckOptions(checkOptions);

            consulClient.registerService(serviceOptions)
                .subscribe().with(
                    result -> LOG.infof("Successfully registered pipestream-engine with Consul, ID: %s", serviceId),
                    throwable -> LOG.errorf(throwable, "Failed to register pipestream-engine with Consul")
                );
                
        } catch (Exception e) {
            LOG.errorf(e, "Error registering pipestream-engine with Consul");
        }
    }
    
    void onStop(@Observes ShutdownEvent ev) {
        if (serviceId != null) {
            LOG.infof("Unregistering pipestream-engine with ID: %s", serviceId);
            
            try {
                consulClient.deregisterService(serviceId)
                    .subscribe().with(
                        result -> LOG.info("Successfully unregistered pipestream-engine"),
                        throwable -> LOG.warnf(throwable, "Error unregistering pipestream-engine")
                    );
            } catch (Exception e) {
                LOG.warnf(e, "Failed to unregister pipestream-engine");
            }
        }
    }
}