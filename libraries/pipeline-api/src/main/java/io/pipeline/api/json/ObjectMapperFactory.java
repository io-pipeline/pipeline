package io.pipeline.api.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Factory for creating consistently configured ObjectMapper instances.
 * This ensures all JSON serialization/deserialization across the pipeline
 * uses the same configuration.
 * 
 * Configuration includes:
 * - Alphabetical property sorting
 * - Map entries ordered by keys
 * - Snake_case naming strategy (preferred by data scientists)
 * - ISO-8601 date/time formatting
 * - JavaTimeModule for proper Java 8 time support
 */
public final class ObjectMapperFactory {
    
    private ObjectMapperFactory() {
        // Prevent instantiation
    }
    
    /**
     * Creates a fully configured ObjectMapper with all standard settings.
     * This is the primary method that should be used throughout the application.
     */
    public static ObjectMapper createConfiguredMapper() {
        return JsonMapper.builder()
                // Sort properties alphabetically for consistent output
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                
                // Use snake_case naming strategy (data scientist preference)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                
                // Write dates as ISO-8601 strings, not timestamps
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
                
                // Don't fail on unknown properties (forward compatibility)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                
                // Add Java 8 time support
                .addModule(new JavaTimeModule())
                
                .build();
    }
    
    /**
     * Creates a minimal ObjectMapper without ordering or special naming.
     * Use this only when you need to match external JSON formats exactly.
     */
    public static ObjectMapper createMinimalMapper() {
        return JsonMapper.builder()
                // Don't fail on unknown properties
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                
                // Add Java 8 time support
                .addModule(new JavaTimeModule())
                
                .build();
    }
    
    /**
     * Applies standard configuration to an existing ObjectMapper.
     * Useful for customizing Quarkus-provided mappers.
     */
    public static void configureMapper(ObjectMapper mapper) {
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
    }
}