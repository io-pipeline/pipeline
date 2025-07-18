package io.pipeline.api.model;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for PipelineConfig.
 */
@QuarkusIntegrationTest
public class PipelineConfigIT extends PipelineConfigTestBase {

    private final ObjectMapper objectMapper;
    
    public PipelineConfigIT(){
        objectMapper = MapperFactory.getObjectMapper();
    }
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}