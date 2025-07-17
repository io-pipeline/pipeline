package com.rokkon.pipeline.consul.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.consul.test.SimpleConsulClientFactory;
import com.rokkon.pipeline.util.ObjectMapperFactory;
import io.quarkus.logging.Log;
import io.vertx.mutiny.ext.consul.ConsulClient;

import java.lang.reflect.Field;
import java.time.Duration;

/**
 * Test context that provides utilities for Consul integration tests.
 */
public class ConsulTestContext {
    
    private final String namespace;
    private final ConsulClient consulClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final boolean shouldCleanup;
    
    public ConsulTestContext(String namespace, Duration timeout, boolean shouldCleanup) {
        this.namespace = namespace;
        this.timeout = timeout;
        this.shouldCleanup = shouldCleanup;
        this.consulClient = SimpleConsulClientFactory.getTestClient();
        this.objectMapper = ObjectMapperFactory.createConfiguredMapper();
    }
    
    /**
     * Get the unique namespace for this test.
     */
    public String namespace() {
        return namespace;
    }
    
    /**
     * Get the Consul client.
     */
    public ConsulClient consulClient() {
        return consulClient;
    }
    
    /**
     * Get the configured ObjectMapper.
     */
    public ObjectMapper objectMapper() {
        return objectMapper;
    }
    
    /**
     * Create a service instance with dependencies injected.
     */
    public <T> ServiceBuilder<T> createService(Class<T> serviceClass) {
        return new ServiceBuilder<>(serviceClass);
    }
    
    /**
     * Cleanup the test namespace if enabled.
     */
    public void cleanup() {
        if (shouldCleanup && namespace != null && consulClient != null) {
            try {
                Log.infof("Cleaning up test namespace: %s", namespace);
                consulClient.deleteValues(namespace)
                    .onItem().invoke(result -> Log.infof("Deleted test namespace %s: %s", namespace, result))
                    .onFailure().invoke(t -> Log.warnf(t, "Failed to delete test namespace %s", namespace))
                    .await().atMost(timeout);
            } catch (Exception e) {
                Log.warnf(e, "Error during cleanup of namespace %s", namespace);
            }
        }
    }
    
    /**
     * Fluent builder for creating service instances.
     */
    public class ServiceBuilder<T> {
        private final Class<T> serviceClass;
        private String kvPrefix;
        private final java.util.Map<String, Object> dependencies = new java.util.HashMap<>();
        
        private ServiceBuilder(Class<T> serviceClass) {
            this.serviceClass = serviceClass;
            this.kvPrefix = namespace; // Default to test namespace
        }
        
        public ServiceBuilder<T> withKvPrefix(String kvPrefix) {
            this.kvPrefix = kvPrefix;
            return this;
        }
        
        public ServiceBuilder<T> withDependency(String fieldName, Object dependency) {
            this.dependencies.put(fieldName, dependency);
            return this;
        }
        
        public T build() {
            try {
                T service = serviceClass.getDeclaredConstructor().newInstance();
                
                // Inject common fields
                injectField(service, "consulClient", consulClient);
                injectField(service, "objectMapper", objectMapper);
                injectField(service, "kvPrefix", kvPrefix);
                
                // Inject custom dependencies
                for (java.util.Map.Entry<String, Object> entry : dependencies.entrySet()) {
                    injectField(service, entry.getKey(), entry.getValue());
                }
                
                return service;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create service instance", e);
            }
        }
        
        private void injectField(Object target, String fieldName, Object value) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (NoSuchFieldException e) {
                // Field doesn't exist in this service, skip
                Log.debugf("Field %s not found in %s", fieldName, target.getClass().getSimpleName());
            } catch (Exception e) {
                Log.warnf(e, "Failed to inject field %s in %s", fieldName, target.getClass().getSimpleName());
            }
        }
    }
}