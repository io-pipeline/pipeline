package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.KeyValueOptions;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.KeyValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CAS functionality in ClusterService using mocks.
 * Note: Some CAS tests are disabled in unit tests as they require real Consul behavior.
 * Run the integration tests (ClusterServiceCasIT) for full CAS testing.
 */
class ClusterServiceCasTest extends ClusterServiceCasTestBase {
    
    @Mock
    private ConsulClient mockConsulClient;
    
    private KeyValue mockKeyValue;
    
    @Captor
    private ArgumentCaptor<KeyValueOptions> optionsCaptor;
    
    private AtomicLong casIndex = new AtomicLong(0);
    
    @Override
    @BeforeEach
    void setupDependencies() {
        MockitoAnnotations.openMocks(this);
        
        // Create the service with mocked dependencies
        clusterService = new ClusterServiceImpl();
        clusterService.consulClient = mockConsulClient;
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        clusterService.objectMapper = mapper;
        clusterService.kvPrefix = "test";
        
        // Setup basic mock behavior
        setupBasicMocks();
    }
    
    private void setupBasicMocks() {
        // Track created keys
        java.util.Set<String> createdKeys = new java.util.HashSet<>();
        java.util.Map<String, String> storedValues = new java.util.HashMap<>();
        
        // Mock getValue to simulate cluster doesn't exist initially, then exists after creation
        when(mockConsulClient.getValue(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (createdKeys.contains(key)) {
                // Return the stored value with current CAS index
                KeyValue kv = new KeyValue();
                kv.setKey(key);
                kv.setValue(storedValues.getOrDefault(key, "{}"));
                kv.setModifyIndex(casIndex.get());
                return Uni.createFrom().item(kv);
            }
            return Uni.createFrom().nullItem();
        });
        
        // Mock putValue for non-CAS operations
        when(mockConsulClient.putValue(anyString(), anyString())).thenReturn(Uni.createFrom().item(true));
        
        // Mock putValueWithOptions for CAS operations
        when(mockConsulClient.putValueWithOptions(anyString(), anyString(), any(KeyValueOptions.class)))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                KeyValueOptions options = invocation.getArgument(2);
                
                // For creation operations (CAS index 0), always succeed
                if (options.getCasIndex() == 0) {
                    createdKeys.add(key);
                    storedValues.put(key, value);
                    casIndex.incrementAndGet();
                    return Uni.createFrom().item(true);
                }
                // For updates, simulate CAS: only succeed if index matches
                if (options.getCasIndex() == casIndex.get()) {
                    storedValues.put(key, value);
                    casIndex.incrementAndGet();
                    return Uni.createFrom().item(true);
                } else {
                    return Uni.createFrom().item(false);
                }
            });
        
        // Mock deleteValue
        when(mockConsulClient.deleteValue(anyString())).thenReturn(Uni.createFrom().voidItem());
        when(mockConsulClient.deleteValues(anyString())).thenReturn(Uni.createFrom().voidItem());
        
        // Mock getKeys for listing
        when(mockConsulClient.getKeys(anyString())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            List<String> matchingKeys = createdKeys.stream()
                .filter(key -> key.startsWith(prefix))
                .collect(java.util.stream.Collectors.toList());
            return Uni.createFrom().item(matchingKeys);
        });
        
        // Mock getValues for listing
        when(mockConsulClient.getValues(anyString())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            List<KeyValue> values = createdKeys.stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> {
                    KeyValue kv = new KeyValue();
                    kv.setKey(key);
                    kv.setValue(storedValues.getOrDefault(key, "{}"));
                    kv.setModifyIndex(casIndex.get());
                    return kv;
                })
                .collect(java.util.stream.Collectors.toList());
            return Uni.createFrom().item(values);
        });
    }
    
    // Disable concurrent tests in unit tests as they require real Consul behavior
    @Override
    @Disabled("Concurrent CAS tests require real Consul - run integration tests instead")
    void testConcurrentClusterCreation() {
        // This test requires real Consul to properly test concurrent behavior
    }
    
    @Override
    @Disabled("Concurrent CAS tests require real Consul - run integration tests instead")
    void testConcurrentClusterMetadataUpdate() {
        // This test requires real Consul to properly test concurrent behavior
    }
    
    @Override
    @Disabled("Concurrent CAS tests require real Consul - run integration tests instead")
    void testConcurrentClusterConfigUpdate() {
        // This test requires real Consul to properly test concurrent behavior
    }
    
    // The retry mechanism test can work with mocks
    // Other tests from the base class will run with mocks
}