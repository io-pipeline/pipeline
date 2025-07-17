package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for StepType enum serialization/deserialization.
 * Tests all step types used in pipeline configuration.
 */
public abstract class StepTypeTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testSerializePipeline() throws Exception {
        String json = getObjectMapper().writeValueAsString(StepType.PIPELINE);
        assertEquals("\"PIPELINE\"", json);
    }

    @Test
    public void testSerializeInitialPipeline() throws Exception {
        String json = getObjectMapper().writeValueAsString(StepType.CONNECTOR);
        assertEquals("\"CONNECTOR\"", json);
    }

    @Test
    public void testSerializeSink() throws Exception {
        String json = getObjectMapper().writeValueAsString(StepType.SINK);
        assertEquals("\"SINK\"", json);
    }

    @Test
    public void testDeserializePipeline() throws Exception {
        StepType type = getObjectMapper().readValue("\"PIPELINE\"", StepType.class);
        assertEquals(StepType.PIPELINE, type);
    }

    @Test
    public void testDeserializeInitialPipeline() throws Exception {
        StepType type = getObjectMapper().readValue("\"CONNECTOR\"", StepType.class);
        assertEquals(StepType.CONNECTOR, type);
    }

    @Test
    public void testDeserializeSink() throws Exception {
        StepType type = getObjectMapper().readValue("\"SINK\"", StepType.class);
        assertEquals(StepType.SINK, type);
    }

    @Test
    public void testAllValues() {
        StepType[] values = StepType.values();
        assertEquals(3, values.length);
        assertArrayEquals(new Object[]{StepType.PIPELINE, StepType.CONNECTOR, StepType.SINK}, values);
    }

    @Test
    public void testRoundTripAllValues() throws Exception {
        for (StepType stepType : StepType.values()) {
            String json = getObjectMapper().writeValueAsString(stepType);
            StepType deserialized = getObjectMapper().readValue(json, StepType.class);
            assertEquals(stepType, deserialized);
        }
    }

    @Test
    public void testInvalidDeserialization() {
        // Test invalid enum values
        InvalidFormatException invalidTypeException = assertThrows(InvalidFormatException.class, 
            () -> getObjectMapper().readValue("\"INVALID_TYPE\"", StepType.class));
        assertTrue(invalidTypeException.getMessage().contains("not one of the values accepted") ||
                   invalidTypeException.getMessage().contains("Cannot deserialize value"));
            
        InvalidFormatException emptyStringException = assertThrows(InvalidFormatException.class, 
            () -> getObjectMapper().readValue("\"\"", StepType.class));
        assertTrue(emptyStringException.getMessage().contains("Cannot coerce empty String"));
    }

    @Test
    public void testCaseInsensitiveDeserialization() {
        // By default, Jackson is case-sensitive for enums
        Exception lowercaseException = assertThrows(Exception.class, 
            () -> getObjectMapper().readValue("\"pipeline\"", StepType.class));
        assertTrue(lowercaseException.getMessage().contains("not one of the values accepted"));
            
        Exception titlecaseException = assertThrows(Exception.class, 
            () -> getObjectMapper().readValue("\"Pipeline\"", StepType.class));
        assertTrue(titlecaseException.getMessage().contains("not one of the values accepted"));
    }

    @Test
    public void testNullHandling() throws Exception {
        String json = getObjectMapper().writeValueAsString(null);
        assertEquals("null", json);
        
        StepType type = getObjectMapper().readValue("null", StepType.class);
        assertNull(type);
    }

    @Test
    public void testEnumInObject() throws Exception {
        // Test enum serialization within a Map (avoid inner class deserialization issues)
        java.util.Map<String, Object> obj = new java.util.HashMap<>();
        obj.put("stepType", StepType.PIPELINE);
        
        String json = getObjectMapper().writeValueAsString(obj);
        assertTrue(json.contains("\"stepType\":\"PIPELINE\""));
        
        // Test deserialization from JSON string
        String testJson = "{\"stepType\":\"PIPELINE\"}";
        java.util.Map<String, Object> deserialized = getObjectMapper().readValue(
            testJson, 
            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}
        );
        assertEquals("PIPELINE", deserialized.get("stepType"));
    }

    @Test
    public void testValueOf() {
        // Test standard enum valueOf behavior
        assertEquals(StepType.PIPELINE, StepType.valueOf("PIPELINE"));
        assertEquals(StepType.CONNECTOR, StepType.valueOf("CONNECTOR"));
        assertEquals(StepType.SINK, StepType.valueOf("SINK"));
        
        // Test invalid enum value
        assertThrows(IllegalArgumentException.class, () -> StepType.valueOf("INVALID"));
    }
}