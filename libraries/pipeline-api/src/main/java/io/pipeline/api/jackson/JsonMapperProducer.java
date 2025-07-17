package io.pipeline.api.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.pipeline.api.json.ObjectMapperFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * CDI producer for Jackson ObjectMapper with consistent configuration
 * across the pipeline system.
 * 
 * This uses the centralized ObjectMapperFactory to ensure consistent
 * JSON serialization configuration throughout the application.
 * 
 * @deprecated Use Quarkus auto-configuration with PipelineObjectMapperCustomizer instead.
 *             This producer is kept for backward compatibility but should not be used
 *             in new code. Quarkus will automatically configure all ObjectMapper instances.
 */
@ApplicationScoped
@Deprecated
public class JsonMapperProducer {

    @Produces
    @Singleton
    public JsonMapper createObjectMapper() {
        // Use the centralized factory configuration
        ObjectMapper mapper = ObjectMapperFactory.createConfiguredMapper();
        
        // Cast is safe because ObjectMapperFactory creates JsonMapper instances
        return (JsonMapper) mapper;
    }
}