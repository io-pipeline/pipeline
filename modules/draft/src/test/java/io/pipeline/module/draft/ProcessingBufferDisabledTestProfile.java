package io.pipeline.module.draft;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that disables the processing buffer for testing.
 * This profile validates that the interceptor does nothing when enabled=false.
 */
public class ProcessingBufferDisabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "processing.buffer.enabled", "false",
            "processing.buffer.capacity", "10",
            "processing.buffer.directory", "target/test-data/disabled", 
            "processing.buffer.prefix", "disabled_test"
        );
    }
}