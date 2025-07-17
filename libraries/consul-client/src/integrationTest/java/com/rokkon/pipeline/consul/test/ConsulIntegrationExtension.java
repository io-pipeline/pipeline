package com.rokkon.pipeline.consul.test;

import io.quarkus.logging.Log;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Field;
import java.time.Duration;

/**
 * JUnit 5 extension that provides Consul test utilities for integration tests.
 */
public class ConsulIntegrationExtension implements BeforeEachCallback, AfterEachCallback {
    
    private static final String CONTEXT_KEY = "consul.test.context";
    
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Get the test instance
        Object testInstance = context.getRequiredTestInstance();
        Class<?> testClass = testInstance.getClass();
        
        // Get the annotation configuration
        ConsulIntegrationTest annotation = testClass.getAnnotation(ConsulIntegrationTest.class);
        if (annotation == null) {
            return; // Should not happen, but be defensive
        }
        
        // Generate unique namespace
        String namespacePrefix = annotation.namespacePrefix();
        if (namespacePrefix.isEmpty()) {
            namespacePrefix = testClass.getSimpleName().toLowerCase();
        }
        String namespace = String.format("%s-%s-%d", 
            namespacePrefix, 
            context.getRequiredTestMethod().getName(),
            System.currentTimeMillis());
        
        // Create test context
        ConsulTestContext testContext = new ConsulTestContext(
            namespace,
            Duration.ofSeconds(annotation.timeoutSeconds()),
            annotation.cleanup()
        );
        
        // Store in extension context for cleanup
        context.getStore(ExtensionContext.Namespace.create(testClass))
            .put(CONTEXT_KEY, testContext);
        
        // Inject into @ConsulTest fields
        injectConsulTestFields(testInstance, testContext);
        
        Log.infof("Initialized ConsulTestContext with namespace: %s", namespace);
    }
    
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        // Get the test context
        ConsulTestContext testContext = context.getStore(
            ExtensionContext.Namespace.create(context.getRequiredTestClass()))
            .remove(CONTEXT_KEY, ConsulTestContext.class);
        
        if (testContext != null) {
            testContext.cleanup();
        }
    }
    
    private void injectConsulTestFields(Object testInstance, ConsulTestContext testContext) {
        Class<?> clazz = testInstance.getClass();
        
        // Walk up the inheritance hierarchy to support base test classes
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(ConsulTest.class)) {
                    if (!field.getType().equals(ConsulTestContext.class)) {
                        throw new IllegalStateException(
                            String.format("Field %s annotated with @ConsulTest must be of type ConsulTestContext",
                                field.getName()));
                    }
                    
                    try {
                        field.setAccessible(true);
                        field.set(testInstance, testContext);
                        Log.debugf("Injected ConsulTestContext into field: %s", field.getName());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to inject ConsulTestContext", e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}