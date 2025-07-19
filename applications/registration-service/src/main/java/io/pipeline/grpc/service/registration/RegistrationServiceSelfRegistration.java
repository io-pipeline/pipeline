package io.pipeline.grpc.service.registration;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Self-registers the registration service with Consul on startup
 */
@ApplicationScoped
public class RegistrationServiceSelfRegistration {
    
    private static final Logger log = Logger.getLogger(RegistrationServiceSelfRegistration.class);
    
    @Inject
    ConsulClient consulClient;
    
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "registration-service")
    String applicationName;
    
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String applicationVersion;
    
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "39100")
    int httpPort;
    
    @ConfigProperty(name = "quarkus.profile")
    String profile;
    
    void onStart(@Observes StartupEvent ev) {
        registerWithConsul()
            .subscribe().with(
                v -> log.info("Successfully registered registration service with Consul"),
                throwable -> log.error("Failed to register registration service with Consul", throwable)
            );
    }
    
    private Uni<Void> registerWithConsul() {
        String hostname = getHostname();
        String serviceId = applicationName + "-" + hostname + "-" + httpPort;
        
        log.infof("Registering registration service with Consul: %s at %s:%d", serviceId, hostname, httpPort);
        
        ServiceOptions serviceOptions = new ServiceOptions()
            .setId(serviceId)
            .setName(applicationName)
            .setTags(List.of("grpc", "registration", "core-service", "version:" + applicationVersion))
            .setAddress(hostname)
            .setPort(httpPort)
            .setMeta(Map.of(
                "service-type", "registration",
                "version", applicationVersion,
                "grpc-enabled", "true",
                "http-enabled", "true"
            ));
        
        // Add health check
        CheckOptions checkOptions = new CheckOptions()
            .setName("Registration Service Health Check")
            .setGrpc(hostname + ":" + httpPort)
            .setInterval("10s")
            .setDeregisterAfter("1m");
        
        serviceOptions.setCheckOptions(checkOptions);
        
        return consulClient.registerService(serviceOptions)
            .replaceWithVoid();
    }
    
    private String getHostname() {
        try {
            // In dev mode, use localhost
            if ("dev".equals(profile)) {
                return "localhost";
            }
            
            // First, try to get from explicit service host variable
            String envHost = System.getenv("REGISTRATION_SERVICE_HOST");
            if (envHost != null && !envHost.isEmpty()) {
                return envHost;
            }
            
            // Otherwise, use container hostname
            String dockerHostname = System.getenv("HOSTNAME");
            if (dockerHostname != null && !dockerHostname.isEmpty()) {
                return dockerHostname;
            }
            
            // Fall back to service name in Docker network
            return applicationName;
            
        } catch (Exception e) {
            log.warn("Failed to determine hostname, using application name", e);
            return applicationName;
        }
    }
}