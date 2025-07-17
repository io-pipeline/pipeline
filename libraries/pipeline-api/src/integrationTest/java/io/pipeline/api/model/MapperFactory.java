package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipeline.api.json.ObjectMapperFactory;

/**
 * Factory for creating and providing a singleton ObjectMapper instance for integration tests.
 * This uses the centralized ObjectMapperFactory to ensure consistent configuration
 * across all tests.
 */
public class MapperFactory {
    
    private static final ObjectMapper INSTANCE;
    
    static {
        // Use the centralized factory for consistent configuration
        INSTANCE = ObjectMapperFactory.createConfiguredMapper();
    }
    
    /**
     * Returns the singleton ObjectMapper instance.
     * 
     * @return the configured ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return INSTANCE;
    }
    
    // Private constructor to prevent instantiation
    private MapperFactory() {
    }
}