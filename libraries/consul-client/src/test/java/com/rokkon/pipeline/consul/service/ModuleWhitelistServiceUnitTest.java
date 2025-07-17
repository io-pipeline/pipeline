package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.commons.validation.EmptyValidationResult;
import com.rokkon.pipeline.testing.util.UnifiedTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.InjectMock;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

/**
 * Unit test for ModuleWhitelistService that uses mocked dependencies.
 * Tests service logic without requiring real Consul.
 */
@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
class ModuleWhitelistServiceUnitTest extends ModuleWhitelistServiceTestBase {
    
    @Inject
    ModuleWhitelistService moduleWhitelistServiceImpl;
    
    @InjectMock
    ClusterService clusterServiceImpl;
    
    @InjectMock
    ConsulClient consulClient;
    
    // Track stored values to simulate Consul KV store
    private final java.util.Map<String, String> kvStore = new java.util.HashMap<>();
    
    @Override
    void setupDependencies() {
        this.moduleWhitelistService = moduleWhitelistServiceImpl;
        this.clusterService = clusterServiceImpl;
    }
    
    @BeforeEach
    void setupMocks() {
        // Clear KV store before each test
        kvStore.clear();
        
        // Mock successful cluster operations
        Mockito.when(clusterServiceImpl.createCluster(Mockito.anyString()))
            .thenReturn(Uni.createFrom().item(EmptyValidationResult.instance()));
        
        Mockito.when(clusterServiceImpl.deleteCluster(Mockito.anyString()))
            .thenReturn(Uni.createFrom().item(EmptyValidationResult.instance()));
        
        // Mock ConsulClient for health service nodes (verifyModuleExistsInConsul)
        var healthServiceList = new io.vertx.ext.consul.ServiceEntryList();
        healthServiceList.setIndex(0);
        healthServiceList.setList(java.util.List.of(new io.vertx.ext.consul.ServiceEntry()));
        Mockito.when(consulClient.healthServiceNodes(Mockito.anyString(), Mockito.anyBoolean()))
            .thenReturn(Uni.createFrom().item(healthServiceList));
        
        // Mock ConsulClient for getValue (loading cluster config from our kvStore)
        Mockito.when(consulClient.getValue(Mockito.anyString()))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                String value = kvStore.get(key);
                
                if (value != null) {
                    var keyValue = new io.vertx.ext.consul.KeyValue();
                    keyValue.setKey(key);
                    keyValue.setValue(value);
                    return Uni.createFrom().item(keyValue);
                }
                
                // If not in store, create default cluster config
                if (key != null && key.contains("/clusters/") && key.endsWith("/config") && !key.contains("/null/")) {
                    String clusterName = key.substring(key.indexOf("/clusters/") + 10, key.lastIndexOf("/config"));
                    var keyValue = new io.vertx.ext.consul.KeyValue();
                    keyValue.setKey(key);
                    keyValue.setValue("{\"clusterName\":\"" + clusterName + "\",\"pipelineModuleMap\":{\"availableModules\":{}}}");
                    return Uni.createFrom().item(keyValue);
                }
                
                return Uni.createFrom().nullItem();
            });
        
        // Mock ConsulClient for putValue (saving cluster config to our kvStore)
        Mockito.when(consulClient.putValue(Mockito.anyString(), Mockito.anyString()))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                kvStore.put(key, value);
                return Uni.createFrom().item(true);
            });
        
        // Mock putValueWithOptions for CAS operations
        Mockito.when(consulClient.putValueWithOptions(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                // Always succeed for unit tests
                kvStore.put(key, value);
                return Uni.createFrom().item(true);
            });
    }
}