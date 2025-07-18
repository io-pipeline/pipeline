package io.pipeline.api.model;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for advanced PipelineConfig scenarios.
 */
@QuarkusIntegrationTest
public class PipelineConfigAdvancedIT extends PipelineConfigAdvancedTestBase {

    private final ObjectMapper objectMapper;
    
    public PipelineConfigAdvancedIT() {
            objectMapper = MapperFactory.getObjectMapper();
    }
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}