package io.pipeline.consul.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.KeyValueOptions;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for Consul services that provides CAS (Compare-And-Swap) operations
 * to ensure safe concurrent updates.
 */
public abstract class ConsulServiceBase {
    private static final Logger LOG = Logger.getLogger(ConsulServiceBase.class);
    
    protected static final int MAX_CAS_RETRIES = 5;
    protected static final Duration RETRY_DELAY = Duration.ofMillis(100);
    
    @Inject
    protected ConsulClient consulClient;
    
    @Inject
    protected ObjectMapper objectMapper;
    
    @ConfigProperty(name = "pipeline.consul.kv-prefix", defaultValue = "pipeline")
    protected String kvPrefix;
    
    /**
     * Creates a new key-value pair in Consul using CAS with index 0.
     * This ensures the key is only created if it doesn't already exist.
     * 
     * @param key The Consul key
     * @param value The value to store
     * @return Uni<Boolean> indicating success or failure
     */
    protected Uni<Boolean> createWithCas(String key, String value) {
        KeyValueOptions options = new KeyValueOptions().setCasIndex(0L);
        return consulClient.putValueWithOptions(key, value, options);
    }
    
    /**
     * Updates an existing key-value pair in Consul using CAS.
     * This method will retry up to MAX_CAS_RETRIES times if there are concurrent updates.
     * 
     * @param key The Consul key
     * @param updateFunction Function that takes the current value and returns the updated value
     * @param <T> The type of the value being updated
     * @return Uni<Boolean> indicating success or failure
     */
    protected <T> Uni<Boolean> updateWithCas(String key, Class<T> valueClass, 
                                           java.util.function.Function<T, T> updateFunction) {
        AtomicInteger retryCount = new AtomicInteger(0);
        return updateWithCasInternal(key, valueClass, updateFunction, retryCount);
    }
    
    private <T> Uni<Boolean> updateWithCasInternal(String key, Class<T> valueClass, 
                                                  java.util.function.Function<T, T> updateFunction,
                                                  AtomicInteger retryCount) {
        return consulClient.getValue(key)
            .flatMap(keyValue -> {
                if (keyValue == null || keyValue.getValue() == null) {
                    LOG.warnf("Key not found for CAS update: %s", key);
                    return Uni.createFrom().item(false);
                }
                
                try {
                    // Deserialize current value
                    T currentValue = objectMapper.readValue(keyValue.getValue(), valueClass);
                    
                    // Apply update function
                    T updatedValue = updateFunction.apply(currentValue);
                    
                    // Serialize updated value
                    String updatedJson = objectMapper.writeValueAsString(updatedValue);
                    
                    // Attempt CAS update
                    KeyValueOptions options = new KeyValueOptions()
                        .setCasIndex(keyValue.getModifyIndex());
                    
                    return consulClient.putValueWithOptions(key, updatedJson, options)
                        .flatMap(success -> {
                            if (success) {
                                LOG.debugf("CAS update successful for key: %s", key);
                                return Uni.createFrom().item(true);
                            } else if (retryCount.incrementAndGet() < MAX_CAS_RETRIES) {
                                LOG.debugf("CAS update failed for key %s, retrying (%d/%d)", 
                                    key, retryCount.get(), MAX_CAS_RETRIES);
                                return Uni.createFrom().voidItem()
                                    .onItem().delayIt().by(RETRY_DELAY)
                                    .flatMap(v -> updateWithCasInternal(key, valueClass, updateFunction, retryCount));
                            } else {
                                LOG.warnf("CAS update failed after %d retries for key: %s", MAX_CAS_RETRIES, key);
                                return Uni.createFrom().item(false);
                            }
                        });
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to process CAS update for key: %s", key);
                    return Uni.createFrom().item(false);
                }
            });
    }
    
    /**
     * Performs a create-or-update operation with CAS.
     * If the key doesn't exist, it creates it. If it exists, it updates it.
     * 
     * @param key The Consul key
     * @param initialValue The value to use if creating
     * @param updateFunction Function to update existing value
     * @param <T> The type of the value
     * @return Uni<Boolean> indicating success or failure
     */
    protected <T> Uni<Boolean> createOrUpdateWithCas(String key, T initialValue, Class<T> valueClass,
                                                   java.util.function.Function<T, T> updateFunction) {
        return consulClient.getValue(key)
            .flatMap(keyValue -> {
                if (keyValue == null || keyValue.getValue() == null) {
                    // Key doesn't exist, create it
                    try {
                        String json = objectMapper.writeValueAsString(initialValue);
                        return createWithCas(key, json);
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to serialize initial value for key: %s", key);
                        return Uni.createFrom().item(false);
                    }
                } else {
                    // Key exists, update it
                    return updateWithCas(key, valueClass, updateFunction);
                }
            });
    }
    
    /**
     * Simple put operation without CAS for backward compatibility or non-critical updates.
     * Consider using CAS operations for production use.
     * 
     * @param key The Consul key
     * @param value The value to store
     * @return Uni<Boolean> indicating success or failure
     */
    protected Uni<Boolean> putValueSimple(String key, String value) {
        LOG.debugf("Simple put (no CAS) for key: %s", key);
        return consulClient.putValue(key, value);
    }
}