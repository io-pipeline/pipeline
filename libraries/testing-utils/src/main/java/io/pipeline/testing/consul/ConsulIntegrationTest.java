package io.pipeline.testing.consul;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * Annotation for Consul integration tests.
 * Combines @QuarkusIntegrationTest with Consul-specific test setup.
 * 
 * Features:
 * - Automatic Docker Compose startup with Consul
 * - Namespace isolation per test class
 * - Cleanup after each test
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@QuarkusIntegrationTest
@ExtendWith(ConsulIntegrationExtension.class)
public @interface ConsulIntegrationTest {
    /**
     * The namespace prefix for this test class.
     * Defaults to the test class simple name.
     */
    String namespacePrefix() default "";
    
    /**
     * Timeout for operations in seconds.
     * Defaults to 10 seconds.
     */
    int timeoutSeconds() default 10;
    
    /**
     * Whether to clean the namespace after each test.
     * Defaults to true for test isolation.
     */
    boolean cleanup() default true;
}