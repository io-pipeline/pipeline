package io.pipeline.api.model;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for StepType enum.
 */
@QuarkusIntegrationTest
public class StepTypeIT extends StepTypeTestBase {

    private final ObjectMapper objectMapper;
    
    public StepTypeIT() {
            objectMapper = MapperFactory.getObjectMapper();
    }
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}