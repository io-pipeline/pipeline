package io.pipeline.consul.client.service;

import io.pipeline.api.service.ClusterService;
import io.pipeline.api.service.ModuleWhitelistService;
import io.pipeline.consul.client.test.ConsulClientTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Unit test for ModuleWhitelistService basic functionality.
 * Tests without requiring real Consul.
 */
@QuarkusTest
@TestProfile(ConsulClientTestProfile.class)
class ModuleWhitelistServiceSimpleUnitTest extends ModuleWhitelistServiceSimpleTestBase {

    @Inject
    ModuleWhitelistService whitelistService;

    @Inject
    ClusterService clusterService;

    @Override
    protected ModuleWhitelistService getWhitelistService() {
        return whitelistService;
    }

    @Override
    protected ClusterService getClusterService() {
        return clusterService;
    }
}