package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for KafkaTransportConfig serialization/deserialization.
 * Tests all the enhanced features including DLQ topic derivation, defaults, and producer properties.
 */
public abstract class KafkaTransportConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testDefaultValues() {
        KafkaTransportConfig config = new KafkaTransportConfig(
                "test-topic", null, null, null, null, null);
        
        assertEquals("test-topic", config.topic());
        assertEquals("pipedocId", config.partitionKeyField());
        assertEquals("snappy", config.compressionType());
        assertEquals(16384, config.batchSize());
        assertEquals(10, config.lingerMs());
        assertTrue(config.kafkaProducerProperties().isEmpty());
    }

    @Test
    public void testDlqTopicDerivation() {
        KafkaTransportConfig config = new KafkaTransportConfig(
                "document-processing.parser.input", null, null, null, null, null);
        
        assertEquals("document-processing.parser.input.dlq", config.getDlqTopic());
        
        // Test null topic
        KafkaTransportConfig nullTopicConfig = new KafkaTransportConfig(
                null, null, null, null, null, null);
        assertNull(nullTopicConfig.getDlqTopic());
    }

    @Test
    public void testCustomValues() {
        KafkaTransportConfig config = new KafkaTransportConfig(
                "custom-topic",
                "customerId",
                "lz4",
                32768,
                50,
                Map.of("acks", "all", "max.request.size", "20971520")
        );
        
        assertEquals("custom-topic", config.topic());
        assertEquals("customerId", config.partitionKeyField());
        assertEquals("lz4", config.compressionType());
        assertEquals(32768, config.batchSize());
        assertEquals(50, config.lingerMs());
        assertEquals("all", config.kafkaProducerProperties().get("acks"));
    }

    @Test
    public void testGetAllProducerProperties() {
        KafkaTransportConfig config = new KafkaTransportConfig(
                "test-topic",
                null,
                "gzip",
                65536,
                100,
                Map.of("acks", "all", "retries", "3")
        );
        
        Map<String, String> allProps = config.getAllProducerProperties();
        
        // Check merged properties
        assertEquals("gzip", allProps.get("compression.type"));
        assertEquals("65536", allProps.get("batch.size"));
        assertEquals("100", allProps.get("linger.ms"));
        assertEquals("all", allProps.get("acks"));
        assertEquals("3", allProps.get("retries"));
        
        // Verify immutability
        assertEquals("all", allProps.get("acks"));
        
        // Verify the map is unmodifiable
        try {
            allProps.put("new-key", "new-value");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        KafkaTransportConfig original = new KafkaTransportConfig(
                "pipeline.step.input",
                "documentId",
                "snappy",
                32768,
                20,
                Map.of("max.in.flight.requests.per.connection", "5")
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertTrue(json.contains("\"topic\":\"pipeline.step.input\""));
        assertTrue(json.contains("\"partitionKeyField\":\"documentId\""));
        assertTrue(json.contains("\"compressionType\":\"snappy\""));
        assertTrue(json.contains("\"batchSize\":32768"));
        assertTrue(json.contains("\"lingerMs\":20"));
        assertTrue(json.contains("\"max.in.flight.requests.per.connection\":\"5\""));
        
        // Deserialize
        KafkaTransportConfig deserialized = getObjectMapper().readValue(json, KafkaTransportConfig.class);
        
        assertEquals(original.topic(), deserialized.topic());
        assertEquals(original.partitionKeyField(), deserialized.partitionKeyField());
        assertEquals(original.compressionType(), deserialized.compressionType());
        assertEquals(original.batchSize(), deserialized.batchSize());
        assertEquals(original.lingerMs(), deserialized.lingerMs());
        assertEquals(original.kafkaProducerProperties(), deserialized.kafkaProducerProperties());
    }

    @Test
    public void testMinimalSerialization() throws Exception {
        // Only topic is provided, everything else defaults
        String json = """
            {
                "topic": "minimal-topic"
            }
            """;
        
        KafkaTransportConfig config = getObjectMapper().readValue(json, KafkaTransportConfig.class);
        
        assertEquals("minimal-topic", config.topic());
        assertEquals("pipedocId", config.partitionKeyField());
        assertEquals("snappy", config.compressionType());
        assertEquals(16384, config.batchSize());
        assertEquals(10, config.lingerMs());
        assertEquals("minimal-topic.dlq", config.getDlqTopic());
    }

    @Test
    public void testNegativeAndZeroValues() {
        // Test that negative/zero values are replaced with defaults
        KafkaTransportConfig config = new KafkaTransportConfig(
                "test-topic",
                "",           // blank partition key field
                "",           // blank compression type
                0,            // zero batch size
                -1,           // negative linger ms
                null
        );
        
        assertEquals("pipedocId", config.partitionKeyField());
        assertEquals("snappy", config.compressionType());
        assertEquals(16384, config.batchSize());
        assertEquals(10, config.lingerMs());
    }

    @Test
    public void testTopicNamingConvention() {
        // Test typical pipeline topic naming
        KafkaTransportConfig config = new KafkaTransportConfig(
                "document-processing.parser.input", null, null, null, null, null);
        
        assertEquals("document-processing.parser.input", config.topic());
        assertEquals("document-processing.parser.input.dlq", config.getDlqTopic());
        
        // The validation of no dots in custom topics would be done by validators,
        // not by the model itself
    }

    @Test
    public void testRealWorldConfiguration() throws Exception {
        // Test a configuration that might be used in production
        String json = """
            {
                "topic": "production.document-parser.input",
                "partitionKeyField": "pipedocId",
                "compressionType": "snappy",
                "batchSize": 65536,
                "lingerMs": 50,
                "kafkaProducerProperties": {
                    "acks": "all",
                    "retries": "3",
                    "max.in.flight.requests.per.connection": "5",
                    "enable.idempotence": "true",
                    "max.request.size": "20971520"
                }
            }
            """;
        
        KafkaTransportConfig config = getObjectMapper().readValue(json, KafkaTransportConfig.class);
        
        assertEquals("production.document-parser.input", config.topic());
        assertEquals("snappy", config.compressionType());
        assertEquals(65536, config.batchSize());
        assertEquals(50, config.lingerMs());
        
        // Check important producer properties
        assertEquals("all", config.kafkaProducerProperties().get("acks"));
        assertEquals("true", config.kafkaProducerProperties().get("enable.idempotence"));
        assertEquals("20971520", config.kafkaProducerProperties().get("max.request.size"));
        
        // Verify DLQ topic
        assertEquals("production.document-parser.input.dlq", config.getDlqTopic());
        
        // Verify merged properties include both explicit and additional
        Map<String, String> allProps = config.getAllProducerProperties();
        assertEquals("snappy", allProps.get("compression.type"));
        assertEquals("65536", allProps.get("batch.size"));
        assertEquals("50", allProps.get("linger.ms"));
        assertEquals("all", allProps.get("acks"));
        assertEquals(8, allProps.size()); // 3 explicit + 5 additional
    }
}