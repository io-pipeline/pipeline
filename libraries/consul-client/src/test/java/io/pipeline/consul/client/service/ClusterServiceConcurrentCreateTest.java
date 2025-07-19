package io.pipeline.consul.client.service;

import io.pipeline.common.validation.ValidationResultFactory;
import io.pipeline.api.service.ClusterService;
import io.smallrye.mutiny.Uni;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit test for concurrent cluster creation using mocked dependencies.
 * This test verifies the logic without requiring a real Consul instance.
 */
class ClusterServiceConcurrentCreateTest extends ClusterServiceConcurrentCreateTestBase {
    
    @Mock
    private ClusterService mockClusterService;
    
    private final AtomicBoolean clusterCreated = new AtomicBoolean(false);
    
    @Override
    protected void setupService() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);
        // Set up mock behavior for concurrent cluster creation
        when(mockClusterService.createCluster(anyString()))
            .thenAnswer(invocation -> {
                // Simulate CAS behavior - only first creation succeeds
                if (clusterCreated.compareAndSet(false, true)) {
                    return Uni.createFrom().item(ValidationResultFactory.success());
                } else {
                    return Uni.createFrom().item(
                        ValidationResultFactory.failure("Cluster already exists")
                    );
                }
            });
        
        // Mock cluster exists check
        when(mockClusterService.clusterExists(anyString()))
            .thenAnswer(invocation -> Uni.createFrom().item(clusterCreated.get()));
        
        // Mock list clusters for cleanup
        when(mockClusterService.listClusters())
            .thenReturn(Uni.createFrom().item(Collections.emptyList()));
        
        // Mock delete cluster
        when(mockClusterService.deleteCluster(anyString()))
            .thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
        
        this.clusterService = mockClusterService;
    }
}