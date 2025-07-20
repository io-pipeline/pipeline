package io.pipeline.module.draft;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables the processing buffer for testing the @ProcessingBuffered interceptor.
 * This profile overrides configuration to enable buffer capture during tests.
 */
public class ProcessingBufferTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "processing.buffer.enabled", "true",
            "processing.buffer.capacity", "10",
            "processing.buffer.directory", "target/test-data/interceptor",
            "processing.buffer.prefix", "interceptor_test"
        );
    }
}