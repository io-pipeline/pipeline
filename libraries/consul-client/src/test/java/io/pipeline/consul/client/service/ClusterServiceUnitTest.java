package io.pipeline.consul.client.service;

import io.pipeline.api.service.ClusterService;
import io.pipeline.consul.client.test.ConsulClientTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.InjectMock;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.KeyValue;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for ClusterService that uses mocked dependencies.
 * Tests service logic without requiring real Consul.
 */
@QuarkusTest
@TestProfile(ConsulClientTestProfile.class)
class ClusterServiceUnitTest extends ClusterServiceTestBase {
    
    @Inject
    ClusterService clusterServiceImpl;
    
    @InjectMock
    ConsulClient consulClient;
    
    private Map<String, String> kvStore = new HashMap<>();
    
    @Override
    void setupDependencies() {
        this.clusterService = clusterServiceImpl;
    }
    
    @BeforeEach
    void setupMocks() {
        // Clear test data
        kvStore.clear();
        
        // Set up mock Consul KV operations
        when(consulClient.getValue(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = kvStore.get(key);
            KeyValue kv = null;
            if (value != null) {
                kv = new KeyValue();
                kv.setKey(key);
                kv.setValue(value);
            }
            return io.smallrye.mutiny.Uni.createFrom().item(kv);
        });
        
        when(consulClient.putValue(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            kvStore.put(key, value);
            return io.smallrye.mutiny.Uni.createFrom().item(true);
        });
        
        when(consulClient.deleteValue(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            kvStore.remove(key);
            return io.smallrye.mutiny.Uni.createFrom().voidItem();
        });
        
        when(consulClient.deleteValues(anyString())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            // Remove all keys that start with the prefix
            kvStore.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            return io.smallrye.mutiny.Uni.createFrom().voidItem();
        });
        
        when(consulClient.getKeys(anyString())).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            var keys = kvStore.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList();
            return io.smallrye.mutiny.Uni.createFrom().item(keys);
        });
        
        // Mock putValueWithOptions for CAS operations
        when(consulClient.putValueWithOptions(anyString(), anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            // Always succeed for unit tests
            kvStore.put(key, value);
            return io.smallrye.mutiny.Uni.createFrom().item(true);
        });
    }
}