package io.pipeline.consul.client.api;

import io.pipeline.api.model.ClusterMetadata;
import io.pipeline.api.service.ClusterService;
import io.pipeline.consul.client.test.ConsulClientTestProfile;
import io.pipeline.common.validation.ValidationResultFactory;
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
@TestProfile(ConsulClientTestProfile.class)
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
