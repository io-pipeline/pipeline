package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Base test class for SchemaReference that contains all test logic.
 * Critical for schema registry integration and version management.
 */
public abstract class SchemaReferenceTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testSerialization() throws Exception {
        SchemaReference ref = new SchemaReference("test-schema", 2);
        String json = getObjectMapper().writeValueAsString(ref);
        
        assertTrue(json.contains("\"subject\":\"test-schema\""));
        assertTrue(json.contains("\"version\":2"));
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = "{\"subject\":\"test-schema\",\"version\":2}";
        SchemaReference ref = getObjectMapper().readValue(json, SchemaReference.class);
        
        assertEquals("test-schema", ref.subject());
        assertEquals(2, ref.version());
    }

    @Test
    public void testToIdentifier() {
        SchemaReference ref = new SchemaReference("my-schema", 5);
        assertEquals("my-schema:5", ref.toIdentifier());
        
        // Test with complex subject names
        SchemaReference complexRef = new SchemaReference("com.rokkon.chunker-config-v2", 10);
        assertEquals("com.rokkon.chunker-config-v2:10", complexRef.toIdentifier());
    }

    @Test
    public void testValidation() {
        // Valid cases
        assertDoesNotThrow(() -> new SchemaReference("valid-subject", 1));
        assertDoesNotThrow(() -> new SchemaReference("another-subject", 10));
        assertDoesNotThrow(() -> new SchemaReference("com.example.schema", 999));
        
        // Invalid subject cases
        Exception ex1 = assertThrows(IllegalArgumentException.class, () -> new SchemaReference(null, 1));
        assertTrue(ex1.getMessage().contains("subject cannot be null or blank"));
            
        Exception ex2 = assertThrows(IllegalArgumentException.class, () -> new SchemaReference("", 1));
        assertTrue(ex2.getMessage().contains("subject cannot be null or blank"));
            
        Exception ex3 = assertThrows(IllegalArgumentException.class, () -> new SchemaReference("   ", 1));
        assertTrue(ex3.getMessage().contains("subject cannot be null or blank"));
        
        // Invalid version cases
        Exception ex4 = assertThrows(IllegalArgumentException.class, () -> new SchemaReference("valid", null));
        assertTrue(ex4.getMessage().contains("version cannot be null and must be positive"));
            
        Exception ex5 = assertThrows(IllegalArgumentException.class, () -> new SchemaReference("valid", 0));
        assertTrue(ex5.getMessage().contains("version cannot be null and must be positive"));
            
        Exception ex6 = assertThrows(IllegalArgumentException.class, () -> new SchemaReference("valid", -1));
        assertTrue(ex6.getMessage().contains("version cannot be null and must be positive"));
    }

    @Test
    public void testRoundTrip() throws Exception {
        SchemaReference original = new SchemaReference("round-trip-test", 3);
        String json = getObjectMapper().writeValueAsString(original);
        SchemaReference deserialized = getObjectMapper().readValue(json, SchemaReference.class);
        
        assertEquals(original.subject(), deserialized.subject());
        assertEquals(original.version(), deserialized.version());
        assertEquals(original.toIdentifier(), deserialized.toIdentifier());
    }

    @Test
    public void testFieldOrdering() throws Exception {
        // Test that fields are consistently ordered
        SchemaReference ref = new SchemaReference("ordering-test", 42);
        String json = getObjectMapper().writeValueAsString(ref);
        
        // With our JsonOrderingCustomizer, fields should be alphabetical
        int subjectPos = json.indexOf("subject");
        int versionPos = json.indexOf("version");
        
        assertTrue(subjectPos < versionPos);
    }

    @Test
    public void testTypicalSchemaRegistryUsage() throws Exception {
        // Simulate typical schema registry patterns
        String[] typicalSubjects = {
            "chunker-config",
            "embedder-config", 
            "com.rokkon.pipeline.chunker-v1",
            "pipeline-step-config-value",
            "pipeline-graph-config-value"
        };
        
        for (String subject : typicalSubjects) {
            SchemaReference ref = new SchemaReference(subject, 1);
            String json = getObjectMapper().writeValueAsString(ref);
            SchemaReference deserialized = getObjectMapper().readValue(json, SchemaReference.class);
            
            assertEquals(subject, deserialized.subject());
            assertEquals(1, deserialized.version());
            assertEquals(subject + ":1", deserialized.toIdentifier());
        }
    }

    @Test
    public void testVersionEvolution() throws Exception {
        // Test schema evolution scenario
        String subject = "chunker-config";
        
        // Simulate version evolution
        for (int version = 1; version <= 5; version++) {
            SchemaReference ref = new SchemaReference(subject, version);
            String json = getObjectMapper().writeValueAsString(ref);
            
            assertTrue(json.contains("\"subject\":\"" + subject + "\""));
            assertTrue(json.contains("\"version\":" + version));
            
            SchemaReference deserialized = getObjectMapper().readValue(json, SchemaReference.class);
            assertEquals(subject + ":" + version, deserialized.toIdentifier());
        }
    }
}