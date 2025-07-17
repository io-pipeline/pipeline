package io.pipeline.api.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * Quarkus ObjectMapperCustomizer that automatically applies our standard
 * JSON configuration to all CDI-managed ObjectMapper instances.
 * 
 * This ensures consistent JSON serialization across all Quarkus applications
 * and modules in the pipeline.
 */
@Singleton
public class PipelineObjectMapperCustomizer implements ObjectMapperCustomizer {
    
    @Override
    public void customize(ObjectMapper objectMapper) {
        // Apply our standard configuration
        ObjectMapperFactory.configureMapper(objectMapper);
    }
}