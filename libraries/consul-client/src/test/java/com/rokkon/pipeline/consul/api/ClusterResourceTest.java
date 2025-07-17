package com.rokkon.pipeline.consul.api;

import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.testing.util.UnifiedTestProfile;
import com.rokkon.pipeline.commons.validation.ValidationResultFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.InjectMock;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(UnifiedTestProfile.class)
public class ClusterResourceTest extends ClusterResourceTestBase {

    @InjectMock
    ClusterService clusterService;

    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }

    @BeforeEach
    void setupMocks() {
        when(clusterService.createCluster("test-create-cluster")).thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
        when(clusterService.getCluster("test-get-cluster")).thenReturn(Uni.createFrom().item(Optional.of(new ClusterMetadata("test-get-cluster", Instant.now(), null, Map.of()))));
        when(clusterService.deleteCluster("test-delete-cluster")).thenReturn(Uni.createFrom().item(ValidationResultFactory.success()));
        when(clusterService.getCluster("non-existent-cluster")).thenReturn(Uni.createFrom().item(Optional.empty()));
    }
}
