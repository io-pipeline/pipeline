package com.rokkon.pipeline.consul.test;

import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.KeyValue;
import io.vertx.ext.consul.KeyValueList;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Interface providing default methods for Consul test support using Mutiny client.
 * 
 * This interface provides:
 * - Namespace isolation per test class
 * - Helper methods for KV operations using Mutiny
 * - Cleanup utilities
 * 
 * Tests can implement this interface to get Consul test utilities.
 */
public interface ConsulTestSupport {
    
    Logger LOG = LoggerFactory.getLogger(ConsulTestSupport.class);
    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    
    /**
     * Get the namespace for this test class.
     * By default, uses "test/{ClassName}/"
     */
    default String getTestNamespace() {
        return "test/" + getClass().getSimpleName() + "/";
    }
    
    /**
     * Get a namespaced key.
     */
    default String namespacedKey(String key) {
        return getTestNamespace() + key;
    }
    
    /**
     * Create a ConsulClient configured from environment.
     * Expects consul.host and consul.port to be set.
     */
    default ConsulClient createConsulClient() {
        String host = System.getProperty("consul.host", "localhost");
        int port = Integer.parseInt(System.getProperty("consul.port", "8500"));
        
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(host)
            .setPort(port);
            
        return ConsulClient.create(Vertx.vertx(), options);
    }
    
    /**
     * Put a value in the test namespace.
     */
    default void putValue(String key, String value) {
        createConsulClient()
            .putValue(namespacedKey(key), value)
            .await()
            .atMost(DEFAULT_TIMEOUT);
    }
    
    /**
     * Get a value from the test namespace.
     */
    default Optional<String> getValue(String key) {
        KeyValue kv = createConsulClient()
            .getValue(namespacedKey(key))
            .await()
            .atMost(DEFAULT_TIMEOUT);
            
        return kv != null && kv.getValue() != null 
            ? Optional.of(kv.getValue()) 
            : Optional.empty();
    }
    
    /**
     * Delete a key from the test namespace.
     */
    default void deleteKey(String key) {
        createConsulClient()
            .deleteValue(namespacedKey(key))
            .await()
            .atMost(DEFAULT_TIMEOUT);
    }
    
    /**
     * Clean up all keys in this test's namespace.
     */
    default void cleanupNamespace() {
        String namespace = getTestNamespace();
        LOG.debug("Cleaning up namespace: {}", namespace);
        
        ConsulClient client = createConsulClient();
        try {
            // Get all keys in namespace
            KeyValueList kvList = client
                .getValues(namespace)
                .await()
                .atMost(DEFAULT_TIMEOUT);
                
            if (kvList != null && kvList.getList() != null && !kvList.getList().isEmpty()) {
                LOG.debug("Deleting {} keys from namespace {}", kvList.getList().size(), namespace);
                // Delete all keys with recursive delete
                client.deleteValues(namespace)
                    .await()
                    .atMost(DEFAULT_TIMEOUT);
            }
        } catch (Exception e) {
            LOG.warn("Error cleaning namespace {}: {}", namespace, e.getMessage());
        }
    }
    
    /**
     * Check if a service is registered in Consul.
     */
    default boolean hasService(String serviceName) {
        try {
            ConsulClient client = createConsulClient();
            return !client
                .healthServiceNodes(serviceName, true)
                .await()
                .atMost(DEFAULT_TIMEOUT)
                .getList()
                .isEmpty();
        } catch (Exception e) {
            LOG.warn("Error checking service {}: {}", serviceName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Wait for a service to be healthy using Awaitility.
     */
    default void waitForHealthyService(String serviceName, Duration timeout) {
        org.awaitility.Awaitility.await()
            .atMost(timeout)
            .pollInterval(Duration.ofMillis(500))
            .until(() -> hasService(serviceName));
    }
    
    /**
     * Wait for a key to exist in Consul.
     */
    default void waitForKey(String key, Duration timeout) {
        org.awaitility.Awaitility.await()
            .atMost(timeout)
            .pollInterval(Duration.ofMillis(200))
            .until(() -> getValue(key).isPresent());
    }
}