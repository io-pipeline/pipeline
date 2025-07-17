package com.rokkon.pipeline.testing.util;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * A unified test profile for Quarkus tests.
 * This profile provides common configuration for all tests.
 */
public class UnifiedTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Add any common test configuration here
            "quarkus.log.level", "INFO"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test";
    }
}