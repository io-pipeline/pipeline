package io.pipeline.module.draft;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables the processing buffer for testing.
 * This profile validates that the interceptor works correctly when enabled=true.
 */
public class ProcessingBufferEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "processing.buffer.enabled", "true",
            "processing.buffer.capacity", "10",
            "processing.buffer.directory", "target/test-data/enabled",
            "processing.buffer.prefix", "enabled_test"
        );
    }
}