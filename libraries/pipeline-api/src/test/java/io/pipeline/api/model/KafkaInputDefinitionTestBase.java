package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for KafkaInputDefinition serialization/deserialization.
 * Tests Kafka input configuration for pipeline steps.
 */
public abstract class KafkaInputDefinitionTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testValidConfiguration() {
        KafkaInputDefinition config = new KafkaInputDefinition(
                List.of("topic1", "topic2", "topic3"),
                "my-consumer-group",
                Map.of("auto.offset.reset", "earliest", "max.poll.records", "500")
        );
        
        assertArrayEquals(new Object[]{"topic1", "topic2", "topic3"}, config.listenTopics().toArray());
        assertEquals("my-consumer-group", config.consumerGroupId());
        assertEquals("earliest", config.kafkaConsumerProperties().get("auto.offset.reset"));
        assertEquals("500", config.kafkaConsumerProperties().get("max.poll.records"));
    }

    @Test
    public void testMinimalConfiguration() {
        // Only topics are required, consumer group can be null
        KafkaInputDefinition config = new KafkaInputDefinition(
                List.of("single-topic"),
                null,
                null
        );
        
        assertArrayEquals(new Object[]{"single-topic"}, config.listenTopics().toArray());
        assertNull(config.consumerGroupId());
        assertTrue(config.kafkaConsumerProperties().isEmpty());
    }

    @Test
    public void testMultipleTopics() {
        // Test fan-in scenario with multiple input topics
        KafkaInputDefinition config = new KafkaInputDefinition(
                List.of("pipeline.parser.output", "pipeline.enricher.output", "pipeline.validator.output"),
                "aggregator-group",
                Map.of()
        );
        
        assertEquals(3, config.listenTopics().size());
        assertEquals("aggregator-group", config.consumerGroupId());
    }

    @Test
    public void testNullTopicsValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new KafkaInputDefinition(null, "group", Map.of()));
assertEquals("listenTopics cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testEmptyTopicsValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new KafkaInputDefinition(List.of(), "group", Map.of()));
assertEquals("listenTopics cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testNullTopicInListValidation() {
        // List.of() throws NullPointerException for null elements
        // This is expected Java behavior - List.of() does not accept null values
        assertThrows(NullPointerException.class, () -> List.of("topic1", null, "topic3"));
            
        // Test with ArrayList to properly test our validation
        List<String> topicsWithNull = new java.util.ArrayList<>();
        topicsWithNull.add("topic1");
        topicsWithNull.add(null);
        topicsWithNull.add("topic3");
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new KafkaInputDefinition(
                topicsWithNull, 
                "group", 
                Map.of()
        ));
        assertEquals("listenTopics cannot contain null or blank topics", exception.getMessage());
    }

    @Test
    public void testBlankTopicInListValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new KafkaInputDefinition(
                List.of("topic1", "  ", "topic3"), 
                "group", 
                Map.of()
        ));
        assertEquals("listenTopics cannot contain null or blank topics", exception.getMessage());
    }

    @Test
    public void testImmutability() {
        List<String> mutableTopics = new java.util.ArrayList<>();
        mutableTopics.add("topic1");
        
        Map<String, String> mutableProps = new java.util.HashMap<>();
        mutableProps.put("key", "value");
        
        KafkaInputDefinition config = new KafkaInputDefinition(
                mutableTopics,
                "immutable-group",
                mutableProps
        );
        
        // Try to modify original collections
        mutableTopics.add("topic2");
        mutableProps.put("key2", "value2");
        
        // Config should not be affected
        assertEquals(1, config.listenTopics().size());
        assertEquals(1, config.kafkaConsumerProperties().size());
        
        // Returned collections should be immutable
        // Try to modify returned collections - should throw exception
        assertThrows(UnsupportedOperationException.class, () -> config.listenTopics().add("new-topic"));
        assertThrows(UnsupportedOperationException.class, () -> config.kafkaConsumerProperties().put("new-key", "new-value"));
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        KafkaInputDefinition original = new KafkaInputDefinition(
                List.of("document.input", "metadata.input"),
                "processor-group",
                Map.of(
                    "auto.offset.reset", "latest",
                    "max.poll.records", "1000",
                    "fetch.min.bytes", "1024"
                )
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertTrue(json.contains("\"listenTopics\":[\"document.input\",\"metadata.input\"]"));
        assertTrue(json.contains("\"consumerGroupId\":\"processor-group\""));
        assertTrue(json.contains("\"auto.offset.reset\":\"latest\""));
        
        // Deserialize
        KafkaInputDefinition deserialized = getObjectMapper().readValue(json, KafkaInputDefinition.class);
        
        assertEquals(original.listenTopics(), deserialized.listenTopics());
        assertEquals(original.consumerGroupId(), deserialized.consumerGroupId());
        assertEquals(original.kafkaConsumerProperties(), deserialized.kafkaConsumerProperties());
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "listenTopics": ["pipeline.step1.output", "pipeline.step2.output"],
                "consumerGroupId": "json-consumer-group",
                "kafkaConsumerProperties": {
                    "auto.offset.reset": "earliest",
                    "enable.auto.commit": "false",
                    "max.partition.fetch.bytes": "1048576"
                }
            }
            """;
        
        KafkaInputDefinition config = getObjectMapper().readValue(json, KafkaInputDefinition.class);
        
        assertArrayEquals(new Object[]{"pipeline.step1.output", "pipeline.step2.output"}, config.listenTopics().toArray());
        assertEquals("json-consumer-group", config.consumerGroupId());
        assertEquals(3, config.kafkaConsumerProperties().size());
        assertTrue(config.kafkaConsumerProperties().containsKey("enable.auto.commit"));
    }

    @Test
    public void testNullConsumerGroupId() throws Exception {
        // Consumer group ID is optional - engine will generate one if needed
        String json = """
            {
                "listenTopics": ["events.input"],
                "kafkaConsumerProperties": {}
            }
            """;
        
        KafkaInputDefinition config = getObjectMapper().readValue(json, KafkaInputDefinition.class);
        
        assertArrayEquals(new Object[]{"events.input"}, config.listenTopics().toArray());
        assertNull(config.consumerGroupId());
        assertTrue(config.kafkaConsumerProperties().isEmpty());
    }

    @Test
    public void testRealWorldFanInConfiguration() throws Exception {
        // Test configuration for a step that aggregates from multiple sources
        String json = """
            {
                "listenTopics": [
                    "production.parser.output",
                    "production.enricher.output",
                    "production.validator.output",
                    "production.classifier.output"
                ],
                "consumerGroupId": "production.aggregator.consumer-group",
                "kafkaConsumerProperties": {
                    "auto.offset.reset": "earliest",
                    "max.poll.records": "500",
                    "fetch.min.bytes": "10240",
                    "fetch.max.wait.ms": "500",
                    "session.timeout.ms": "30000",
                    "heartbeat.interval.ms": "3000",
                    "max.partition.fetch.bytes": "10485760",
                    "enable.auto.commit": "false",
                    "isolation.level": "read_committed"
                }
            }
            """;
        
        KafkaInputDefinition config = getObjectMapper().readValue(json, KafkaInputDefinition.class);
        
        assertEquals(4, config.listenTopics().size());
        assertEquals("production.aggregator.consumer-group", config.consumerGroupId());
        assertEquals(9, config.kafkaConsumerProperties().size());
        
        // Verify important consumer properties
        assertEquals("read_committed", config.kafkaConsumerProperties().get("isolation.level"));
        assertEquals("false", config.kafkaConsumerProperties().get("enable.auto.commit"));
        assertEquals("500", config.kafkaConsumerProperties().get("max.poll.records"));
    }
}