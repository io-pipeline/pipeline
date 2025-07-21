package io.pipeline.api.annotation;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Base annotation for automatic service registration with Consul.
 * <p>
 * When applied to a gRPC service class, this annotation will:
 * 1. Register the service directly with Consul using Stork service registrar
 * 2. Make the service discoverable by other services
 * 3. Provide health checks and metadata
 * <p>
 * This is the base annotation that handles Consul registration only.
 * Use {@link PipelineAutoRegister} for modules that also need to register
 * with the pipeline module registry.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @GrpcService
 * @ConsulAutoRegister(
 *     serviceType = "engine",
 *     useHttpPort = true,
 *     metadata = {"role=orchestrator", "version=1.0"}
 * )
 * public class PipeStreamEngineImpl implements PipeStreamEngine {
 *     // ... implementation
 * }
 * }
 * </pre>
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ConsulAutoRegister {
    
    /**
     * The type of service (e.g., "engine", "processor", "filter").
     * This will be included in the service metadata.
     */
    @Nonbinding
    String serviceType() default "service";
    
    /**
     * Whether to use the HTTP port instead of gRPC port for registration.
     * Default is false (use gRPC port).
     */
    @Nonbinding
    boolean useHttpPort() default false;
    
    /**
     * Additional metadata to include with the service registration.
     * Should be in the format: ["key1=value1", "key2=value2"]
     */
    @Nonbinding
    String[] metadata() default {};
    
    /**
     * Whether auto-registration is enabled.
     * Can be overridden by configuration property: consul.auto-register.enabled
     */
    @Nonbinding
    boolean enabled() default true;
}