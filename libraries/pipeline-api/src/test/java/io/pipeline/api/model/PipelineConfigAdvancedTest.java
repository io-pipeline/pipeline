package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit test for advanced PipelineConfig scenarios using Quarkus CDI.
 */
@QuarkusTest
public class PipelineConfigAdvancedTest extends PipelineConfigAdvancedTestBase {

    @Inject
    ObjectMapper objectMapper;

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}