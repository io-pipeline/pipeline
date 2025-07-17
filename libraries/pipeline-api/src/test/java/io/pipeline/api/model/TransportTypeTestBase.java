package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for TransportType enum serialization/deserialization.
 * Tests all transport mechanisms used between pipeline steps.
 */
public abstract class TransportTypeTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testSerializeKafka() throws Exception {
        String json = getObjectMapper().writeValueAsString(TransportType.KAFKA);
        assertEquals("\"KAFKA\"", json);
    }

    @Test
    public void testSerializeGrpc() throws Exception {
        String json = getObjectMapper().writeValueAsString(TransportType.GRPC);
        assertEquals("\"GRPC\"", json);
    }

    @Test
    public void testSerializeInternal() throws Exception {
        String json = getObjectMapper().writeValueAsString(TransportType.INTERNAL);
        assertEquals("\"INTERNAL\"", json);
    }

    @Test
    public void testDeserializeKafka() throws Exception {
        TransportType type = getObjectMapper().readValue("\"KAFKA\"", TransportType.class);
        assertEquals(TransportType.KAFKA, type);
    }

    @Test
    public void testDeserializeGrpc() throws Exception {
        TransportType type = getObjectMapper().readValue("\"GRPC\"", TransportType.class);
        assertEquals(TransportType.GRPC, type);
    }

    @Test
    public void testDeserializeInternal() throws Exception {
        TransportType type = getObjectMapper().readValue("\"INTERNAL\"", TransportType.class);
        assertEquals(TransportType.INTERNAL, type);
    }

    @Test
    public void testAllValues() {
        TransportType[] values = TransportType.values();
        assertEquals(3, values.length);
        assertArrayEquals(new TransportType[]{TransportType.KAFKA, TransportType.GRPC, TransportType.INTERNAL}, values);
    }

    @Test
    public void testRoundTripAllValues() throws Exception {
        for (TransportType transportType : TransportType.values()) {
            String json = getObjectMapper().writeValueAsString(transportType);
            TransportType deserialized = getObjectMapper().readValue(json, TransportType.class);
            assertEquals(transportType, deserialized);
        }
    }

    @Test
    public void testInvalidDeserialization() {
        Exception ex = assertThrows(Exception.class, () -> getObjectMapper().readValue("\"INVALID_TRANSPORT\"", TransportType.class));
        assertTrue(ex.getMessage().contains("not one of the values accepted"));
            
        Exception exception = assertThrows(Exception.class, () -> getObjectMapper().readValue("\"\"", TransportType.class));
        assertTrue(exception.getMessage().contains("Cannot coerce empty String"));
    }

    @Test
    public void testCaseInsensitiveDeserialization() {
        // By default, Jackson is case-sensitive for enums
        Exception exception1 = assertThrows(Exception.class, () -> getObjectMapper().readValue("\"kafka\"", TransportType.class));
        assertTrue(exception1.getMessage().contains("not one of the values accepted"));
            
        Exception exception2 = assertThrows(Exception.class, () -> getObjectMapper().readValue("\"gRPC\"", TransportType.class));
        assertTrue(exception2.getMessage().contains("not one of the values accepted"));
    }

    @Test
    public void testNullHandling() throws Exception {
        String json = getObjectMapper().writeValueAsString(null);
        assertEquals("null", json);
        
        TransportType type = getObjectMapper().readValue("null", TransportType.class);
        assertNull(type);
    }

    @Test
    public void testEnumInObject() throws Exception {
        // Test enum serialization within a Map (avoid inner class deserialization issues)
        java.util.Map<String, Object> obj = new java.util.HashMap<>();
        obj.put("transport", TransportType.KAFKA);
        
        String json = getObjectMapper().writeValueAsString(obj);
        assertTrue(json.contains("\"transport\":\"KAFKA\""));
        
        // Test deserialization from JSON string
        String testJson = "{\"transport\":\"KAFKA\"}";
        java.util.Map<String, Object> deserialized = getObjectMapper().readValue(
            testJson, 
            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}
        );
        assertEquals("KAFKA", deserialized.get("transport"));
    }

    @Test
    public void testValueOf() {
        // Test standard enum valueOf behavior
        assertEquals(TransportType.KAFKA, TransportType.valueOf("KAFKA"));
        assertEquals(TransportType.GRPC, TransportType.valueOf("GRPC"));
        assertEquals(TransportType.INTERNAL, TransportType.valueOf("INTERNAL"));
        
        assertThrows(IllegalArgumentException.class, () -> TransportType.valueOf("INVALID"));
    }
}