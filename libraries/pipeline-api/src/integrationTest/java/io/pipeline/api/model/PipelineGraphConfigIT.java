package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for PipelineGraphConfig.
 * Uses the shared ObjectMapper from MapperFactory to ensure models work without Quarkus injection.
 */
@QuarkusIntegrationTest
public class PipelineGraphConfigIT extends PipelineGraphConfigTestBase {

    @Override
    protected ObjectMapper getObjectMapper() {
        return MapperFactory.getObjectMapper();
    }
}
