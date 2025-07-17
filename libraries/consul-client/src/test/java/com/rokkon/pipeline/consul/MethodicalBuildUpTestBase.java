package com.rokkon.pipeline.consul;

import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.ModuleWhitelistService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.consul.test.TestSeedingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class MethodicalBuildUpTestBase {

    protected static final String DEFAULT_CLUSTER = "default";
    protected static final String TEST_CLUSTER = "test";
    protected static final String TEST_MODULE = "test-module";

    protected abstract ClusterService getClusterService();
    protected abstract ModuleWhitelistService getModuleWhitelistService();
    protected abstract PipelineConfigService getPipelineConfigService();
    protected abstract TestSeedingService getTestSeedingService();

    @Test
    void testMethodicalBuildUp() {
        // Step 0: Consul Started
        getTestSeedingService().seedStep0_ConsulStarted().await().indefinitely();

        // Step 1: Clusters Created
        getTestSeedingService().seedStep1_ClustersCreated().await().indefinitely();
        assertEquals(2, getClusterService().listClusters().await().indefinitely().size());

        // Step 2: Container Accessible
        getTestSeedingService().seedStep2_ContainerAccessible().await().indefinitely();

        // Step 3: Container Registered
        getTestSeedingService().seedStep3_ContainerRegistered().await().indefinitely();

        // Step 4: Empty Pipeline Created
        getTestSeedingService().seedStep4_EmptyPipelineCreated().await().indefinitely();
        assertTrue(getPipelineConfigService().getPipeline(TEST_CLUSTER, "test-pipeline").await().indefinitely().isPresent());

        // Step 5: First Pipeline Step Added
        getTestSeedingService().seedStep5_FirstPipelineStepAdded().await().indefinitely();
        assertEquals(1, getPipelineConfigService().getPipeline(TEST_CLUSTER, "test-pipeline").await().indefinitely().get().pipelineSteps().size());

        // Teardown
        getTestSeedingService().teardownAll().await().indefinitely();
    }
}