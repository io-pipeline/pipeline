package io.pipeline.api.annotation;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable automatic registration of a pipeline module with the registration service.
 * <p>
 * When applied to a class that implements PipeStepProcessor, this annotation will:
 * 1. Automatically discover the registration service via Consul
 * 2. Register the module on startup
 * 3. Unregister the module on shutdown
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @ApplicationScoped
 * @PipelineAutoRegister
 * public class MyModule implements PipeStepProcessor {
 *     // ... module implementation
 * }
 * }
 * </pre>
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PipelineAutoRegister {
    
    /**
     * The type of module (e.g., "processor", "filter", "analyzer").
     * This will be included in the module metadata.
     */
    @Nonbinding
    String moduleType() default "processor";
    
    /**
     * Whether to use the HTTP port instead of gRPC port for registration.
     * Default is false (use gRPC port).
     */
    @Nonbinding
    boolean useHttpPort() default false;
    
    /**
     * Additional metadata to include with the module registration.
     * Should be in the format: ["key1=value1", "key2=value2"]
     */
    @Nonbinding
    String[] metadata() default {};
    
    /**
     * Whether auto-registration is enabled.
     * Can be overridden by configuration property: module.auto-register.enabled
     */
    @Nonbinding
    boolean enabled() default true;
}