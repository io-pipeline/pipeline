package io.pipeline.api.model;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for PipelineClusterConfig.
 */
@QuarkusIntegrationTest
public class PipelineClusterConfigIT extends PipelineClusterConfigTestBase {

    private final ObjectMapper objectMapper;
    
    public PipelineClusterConfigIT() {
        objectMapper = MapperFactory.getObjectMapper();
    }
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}