package com.rokkon.pipeline.consul;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test configuration profile that enables test mode to suppress noisy logs.
 */
public class TestConfiguration implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.log.category.\"com.rokkon.pipeline.consul.service\".level", "DEBUG"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test";
    }
}