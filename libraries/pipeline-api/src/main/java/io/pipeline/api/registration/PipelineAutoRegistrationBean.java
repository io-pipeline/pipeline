package io.pipeline.api.registration;

import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.registration.ModuleId;
import io.pipeline.registration.ModuleInfo;
import io.pipeline.registration.MutinyModuleRegistrationGrpc;
import io.quarkus.arc.All;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.enterprise.inject.Instance;
import io.quarkus.arc.properties.IfBuildProperty;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles automatic registration of pipeline modules annotated with @PipelineAutoRegister.
 * This bean acts as a thin client that discovers annotated processors and registers them
 * with the registration service via gRPC using Stork service discovery.
 * <p>
 * This bean is disabled when module.auto-register.enabled=false to prevent
 * registration service from trying to register with itself.
 */
@ApplicationScoped
@IfBuildProperty(name = "module.auto-register.bean.enabled", stringValue = "true", enableIfMissing = true)
public class PipelineAutoRegistrationBean {
    
    private static final Logger LOG = Logger.getLogger(PipelineAutoRegistrationBean.class);
    
    public PipelineAutoRegistrationBean() {
        LOG.info("PipelineAutoRegistrationBean constructor called");
    }
    
    @Inject
    @All
    List<PipeStepProcessor> processors;
    
    
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;
    
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int httpPort;
    
    @ConfigProperty(name = "quarkus.grpc.server.port", defaultValue = "9000")
    int grpcPort;
    
    @ConfigProperty(name = "module.auto-register.enabled", defaultValue = "true")
    boolean globalEnabled;
    
    @ConfigProperty(name = "module.registration.host")
    Optional<String> moduleHost;
    
    @ConfigProperty(name = "quarkus.http.host")
    Optional<String> quarkusHttpHost;
    
    @ConfigProperty(name = "module.version", defaultValue = "1.0.0")
    String moduleVersion;
    
    // The gRPC client MUST use Stork service discovery with Consul configured in application.properties
    // Required config:
    // quarkus.grpc.clients.registration-service.name-resolver=stork
    // quarkus.stork.registration-service.service-discovery.type=consul
    // quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:localhost}
    // quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}
    // Note: With Consul sidecars, localhost:8500 is the default agent endpoint
    @GrpcClient("registration-service")
    Instance<MutinyModuleRegistrationGrpc.MutinyModuleRegistrationStub> registrationClient;
    
    @ConfigProperty(name = "module.type")
    Optional<String> moduleType;
    
    @ConfigProperty(name = "module.metadata")
    Optional<String> moduleMetadata;
    
    @ConfigProperty(name = "quarkus.grpc.server.use-separate-server", defaultValue = "true")
    boolean useSeparateGrpcServer;
    
    private final Map<String, String> registeredServices = new ConcurrentHashMap<>();
    
    void onStart(@Observes StartupEvent ev) {
        LOG.info("PipelineAutoRegistrationBean.onStart called");
        
        // Check if registration is disabled via runtime configuration (environment variable or system property)
        if (!globalEnabled) {
            LOG.info("Module auto-registration is disabled via runtime configuration (module.auto-register.enabled=false)");
            LOG.info("To enable registration at startup, set MODULE_AUTO_REGISTER_ENABLED=true environment variable");
            return;
        }
        
        // Don't register in test mode
        if (ConfigUtils.isProfileActive("test")) {
            LOG.debug("Skipping registration in test profile");
            return;
        }
        
        // Check if the gRPC client is properly configured and available
        if (registrationClient == null || registrationClient.isUnsatisfied()) {
            LOG.errorf("Registration client not available - Consul service discovery is required for pipeline modules. " +
                     "Please add this configuration to your application.properties:\n" +
                     "quarkus.grpc.clients.registration-service.name-resolver=stork\n" +
                     "quarkus.stork.registration-service.service-discovery.type=consul\n" +
                     "quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:localhost}\n" +
                     "quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}\n" +
                     "Note: With Consul sidecars, localhost:8500 is the default agent endpoint.");
            throw new IllegalStateException("Consul service discovery configuration required for pipeline modules");
        }
        
        try {
            // Scan for beans annotated with @PipelineAutoRegister
            registerAnnotatedProcessors();
            
            // Also register the module itself if configured, but only if no annotated processors were found
            if ((moduleType.isPresent() || moduleMetadata.isPresent()) && processors.stream().noneMatch(p -> p.getClass().isAnnotationPresent(PipelineAutoRegister.class) || (p.getClass().getSuperclass() != null && p.getClass().getSuperclass().isAnnotationPresent(PipelineAutoRegister.class)))) {
                registerSelf();
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error during auto-registration process. Service will continue without registration.");
        }
    }
    
    void onStop(@Observes ShutdownEvent ev) {
        // Skip unregistration if registration was disabled via runtime configuration
        if (!globalEnabled) {
            LOG.info("Skipping unregistration as registration was disabled via runtime configuration (module.auto-register.enabled=false)");
            return;
        }
        
        // Skip unregistration if client is not available
        if (registrationClient == null || registrationClient.isUnsatisfied()) {
            LOG.info("Skipping unregistration as registration client is not available");
            return;
        }
        
        // Unregister all registered modules
        registeredServices.forEach((className, serviceId) -> {
            LOG.infof("Unregistering module %s with ID: %s", className, serviceId);
            
            try {
                registrationClient.get().unregisterModule(
                    ModuleId.newBuilder().setServiceId(serviceId).build()
                )
                .subscribe().with(
                    status -> {
                        if (status.getSuccess()) {
                            LOG.infof("Successfully unregistered module %s", className);
                        } else {
                            LOG.errorf("Failed to unregister module %s", className);
                        }
                    },
                    throwable -> {
                        LOG.debugf(throwable, "Error unregistering module %s", className);
                    }
                );
            } catch (Exception e) {
                LOG.debugf(e, "Failed to unregister module on shutdown");
            }
        });
    }
    
    private void registerAnnotatedProcessors() {
        if (processors == null || processors.isEmpty()) {
            LOG.debug("No PipeStepProcessor beans found");
            return;
        }
        
        // Double-check that registration is enabled via runtime configuration
        if (!globalEnabled) {
            LOG.info("Skipping processor registration as registration is disabled via runtime configuration (module.auto-register.enabled=false)");
            LOG.info("To enable registration at startup, set MODULE_AUTO_REGISTER_ENABLED=true environment variable");
            return;
        }
        
        // Check if client is available
        if (registrationClient == null || registrationClient.isUnsatisfied()) {
            LOG.errorf("Skipping processor registration - Consul service discovery is required for pipeline modules. " +
                     "Please add this configuration to your application.properties:\n" +
                     "quarkus.grpc.clients.registration-service.name-resolver=stork\n" +
                     "quarkus.stork.registration-service.service-discovery.type=consul\n" +
                     "quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:localhost}\n" +
                     "quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}");
            return;
        }
        
        for (PipeStepProcessor processor : processors) {
            Class<?> processorClass = processor.getClass();
            
            // Check for annotation on the actual class (CDI creates proxy subclasses)
            PipelineAutoRegister annotation = processorClass.getAnnotation(PipelineAutoRegister.class);
            if (annotation == null && processorClass.getSuperclass() != null) {
                // CDI proxy - check the superclass for the annotation
                annotation = processorClass.getSuperclass().getAnnotation(PipelineAutoRegister.class);
                LOG.debugf("Checking superclass %s for @PipelineAutoRegister annotation", processorClass.getSuperclass().getName());
            }
            
            if (annotation == null) {
                LOG.debugf("Processor %s is not annotated with @PipelineAutoRegister, skipping", processorClass.getName());
                continue;
            }
            
            if (!annotation.enabled()) {
                LOG.debugf("Auto-registration disabled for processor %s", processorClass.getName());
                continue;
            }
            
            try {
                String host = determineHost();
                int port = annotation.useHttpPort() ? httpPort : grpcPort;
                
                // Build module info for the annotated processor
                var moduleInfoBuilder = ModuleInfo.newBuilder()
                    .setServiceName(processorClass.getSimpleName())
                    .setHost(host)
                    .setPort(port)
                    .putMetadata("version", moduleVersion)
                    .putMetadata("class", processorClass.getName())
                    .putMetadata("type", annotation.moduleType());
                
                // Add custom metadata from annotation
                for (String meta : annotation.metadata()) {
                    String[] parts = meta.split("=", 2);
                    if (parts.length == 2) {
                        moduleInfoBuilder.putMetadata(parts[0].trim(), parts[1].trim());
                    }
                }
                
                ModuleInfo moduleInfo = moduleInfoBuilder.build();
                
                LOG.infof("Registering annotated processor %s at %s:%d", processorClass.getName(), host, port);
                
                // Check if registration client is available before using it
                if (registrationClient == null || registrationClient.isUnsatisfied()) {
                    LOG.errorf("Failed to register processor %s: Consul service discovery is required. " +
                             "Please add this configuration to your application.properties:\n" +
                             "quarkus.grpc.clients.registration-service.name-resolver=stork\n" +
                             "quarkus.stork.registration-service.service-discovery.type=consul\n" +
                             "quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:localhost}\n" +
                             "quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}", 
                             processorClass.getName());
                    return;
                }
                
                // Register with the service using Stork-discovered endpoint
                registrationClient.get().registerModule(moduleInfo)
                    .subscribe().with(
                        status -> {
                            if (status.getSuccess()) {
                                registeredServices.put(processorClass.getName(), status.getConsulServiceId());
                                LOG.infof("Successfully registered processor %s with ID: %s", 
                                    processorClass.getName(), status.getConsulServiceId());
                            } else {
                                LOG.errorf("Failed to register processor %s: %s", 
                                    processorClass.getName(), status.getMessage());
                            }
                        },
                        throwable -> {
                            LOG.errorf(throwable, "Error registering processor %s", processorClass.getName());
                        }
                    );
                    
            } catch (Exception e) {
                LOG.errorf(e, "Failed to register processor %s", processorClass.getName());
            }
        }
    }
    
    private void registerSelf() {
        // Double-check that registration is enabled via runtime configuration
        if (!globalEnabled) {
            LOG.info("Skipping self-registration as registration is disabled via runtime configuration (module.auto-register.enabled=false)");
            LOG.info("To enable registration at startup, set MODULE_AUTO_REGISTER_ENABLED=true environment variable");
            return;
        }
        
        // Check if client is available
        if (registrationClient == null || registrationClient.isUnsatisfied()) {
            LOG.errorf("Skipping self-registration - Consul service discovery is required for pipeline modules. " +
                     "Please add this configuration to your application.properties:\n" +
                     "quarkus.grpc.clients.registration-service.name-resolver=stork\n" +
                     "quarkus.stork.registration-service.service-discovery.type=consul\n" +
                     "quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:localhost}\n" +
                     "quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}");
            return;
        }
        
        try {
            String host = determineHost();
            // Use HTTP port for unified server mode, gRPC port for separate server mode
            int port = useSeparateGrpcServer ? grpcPort : httpPort;
            
            // Build module info for self-registration
            var moduleInfoBuilder = ModuleInfo.newBuilder()
                .setServiceName(applicationName)
                .setHost(host)
                .setPort(port)
                .putMetadata("version", moduleVersion)
                .putMetadata("httpPort", String.valueOf(httpPort))
                .putMetadata("grpcPort", String.valueOf(grpcPort))
                .putMetadata("unifiedServer", String.valueOf(!useSeparateGrpcServer));
            
            // Add metadata from config if available
            if (moduleType.isPresent()) {
                moduleInfoBuilder.putMetadata("type", moduleType.get());
            }
            
            if (moduleMetadata.isPresent()) {
                for (String meta : moduleMetadata.get().split(",")) {
                    String[] parts = meta.trim().split("=", 2);
                    if (parts.length == 2) {
                        moduleInfoBuilder.putMetadata(parts[0], parts[1]);
                    }
                }
            }
            
            ModuleInfo moduleInfo = moduleInfoBuilder.build();
            
            LOG.infof("Self-registering module %s at %s:%d with registration service", applicationName, host, port);
            
            // Check if registration client is available before using it
            if (registrationClient == null || registrationClient.isUnsatisfied()) {
                LOG.errorf("Failed to self-register module %s: Consul service discovery is required. " +
                         "Please add this configuration to your application.properties:\n" +
                         "quarkus.grpc.clients.registration-service.name-resolver=stork\n" +
                         "quarkus.stork.registration-service.service-discovery.type=consul\n" +
                         "quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:localhost}\n" +
                         "quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}", 
                         applicationName);
                return;
            }
            
            // Register with the service using Stork-discovered endpoint
            registrationClient.get().registerModule(moduleInfo)
                .subscribe().with(
                    status -> {
                        if (status.getSuccess()) {
                            registeredServices.put(applicationName, status.getConsulServiceId());
                            LOG.infof("Successfully self-registered module %s with ID: %s", 
                                applicationName, status.getConsulServiceId());
                        } else {
                            LOG.errorf("Failed to self-register module %s: %s", 
                                applicationName, status.getMessage());
                        }
                    },
                    throwable -> {
                        LOG.errorf(throwable, "Error self-registering module %s", applicationName);
                    }
                );
                
        } catch (Exception e) {
            LOG.errorf(e, "Failed to self-register module");
        }
    }
    
    private String determineHost() throws UnknownHostException {
        // First check if explicitly configured
        if (moduleHost.isPresent()) {
            LOG.debugf("Using configured module.registration.host: %s", moduleHost.get());
            return moduleHost.get();
        }
        
        // Second, use standard Quarkus HTTP host (but not if it's 0.0.0.0)
        if (quarkusHttpHost.isPresent() && !"0.0.0.0".equals(quarkusHttpHost.get())) {
            LOG.debugf("Using quarkus.http.host: %s", quarkusHttpHost.get());
            return quarkusHttpHost.get();
        }
        
        // Check for MODULE_HOST env var (set by docker-compose)
        String moduleHostEnv = System.getenv("MODULE_HOST");
        LOG.debugf("MODULE_HOST env var: %s", moduleHostEnv);
        if (moduleHostEnv != null && !moduleHostEnv.isEmpty()) {
            LOG.debugf("Using MODULE_HOST: %s", moduleHostEnv);
            return moduleHostEnv;
        }
        
        // Check for MODULE_REGISTRATION_HOST env var
        String envHost = System.getenv("MODULE_REGISTRATION_HOST");
        if (envHost != null && !envHost.isEmpty()) {
            LOG.debugf("Using MODULE_REGISTRATION_HOST: %s", envHost);
            return envHost;
        }
        
        // Check for Docker environment
        String dockerHost = System.getenv("HOSTNAME");
        if (dockerHost != null && !dockerHost.isEmpty()) {
            LOG.debugf("Using Docker hostname: %s", dockerHost);
            return dockerHost;
        }
        
        // Fall back to local hostname
        String localHost = InetAddress.getLocalHost().getHostName();
        LOG.debugf("Using local hostname: %s", localHost);
        return localHost;
    }
}