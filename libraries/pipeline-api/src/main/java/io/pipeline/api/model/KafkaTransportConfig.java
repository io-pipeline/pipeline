package io.pipeline.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collections;
import java.util.Map;

/**
 * Kafka transport configuration for pipeline step outputs.
 * 
 * <h2>Design Decisions:</h2>
 * <ul>
 *   <li><b>Topic Naming</b>: Follow pattern {@code {pipeline-name}.{step-name}.input}</li>
 *   <li><b>DLQ Topics</b>: Always derived as {@code {topic}.dlq} - not configurable
 *       (Seinfeld "good luck with all that" pattern)</li>
 *   <li><b>Partitioning</b>: Always by pipedocId to ensure CRUD ordering</li>
 *   <li><b>Compression</b>: Default to snappy for performance (Kafka 8MB limit in AWS)</li>
 *   <li><b>Consumer Groups</b>: Per-pipeline naming: {@code {pipeline-name}.consumer-group}</li>
 * </ul>
 * 
 * <h2>Validation Rules:</h2>
 * <ul>
 *   <li>No dots in custom topic names (dots are delimiters)</li>
 *   <li>Topic names must match: {@code ^[a-zA-Z0-9._-]+$}</li>
 *   <li>Maximum topic length: 249 characters</li>
 * </ul>
 * 
 * @param topic Target Kafka topic
 * @param partitionKeyField Field to use for partition key
 * @param compressionType Compression type for Kafka messages
 * @param batchSize Producer batch size in bytes
 * @param lingerMs Producer linger time in milliseconds
 * @param kafkaProducerProperties Additional Kafka producer properties
 * @see PipelineStepConfig.OutputTarget
 * @see KafkaInputDefinition
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Kafka transport configuration")
public record KafkaTransportConfig(
        @JsonProperty("topic") 
        @Schema(description = "Target Kafka topic", example = "document-processing.parser.input")
        String topic,

        @JsonProperty("partitionKeyField") 
        @Schema(description = "Field to use for partition key", defaultValue = "pipedocId", example = "pipedocId")
        String partitionKeyField,

        @JsonProperty("compressionType") 
        @Schema(description = "Compression type for Kafka messages", defaultValue = "snappy")
        String compressionType,

        @JsonProperty("batchSize") 
        @Schema(description = "Producer batch size in bytes", defaultValue = "16384")
        Integer batchSize,

        @JsonProperty("lingerMs") 
        @Schema(description = "Producer linger time in milliseconds", defaultValue = "10")
        Integer lingerMs,

        @JsonProperty("kafkaProducerProperties") 
        @Schema(description = "Additional Kafka producer properties")
        Map<String, String> kafkaProducerProperties
) {
    @JsonCreator
    public KafkaTransportConfig(
            @JsonProperty("topic") String topic,
            @JsonProperty("partitionKeyField") String partitionKeyField,
            @JsonProperty("compressionType") String compressionType,
            @JsonProperty("batchSize") Integer batchSize,
            @JsonProperty("lingerMs") Integer lingerMs,
            @JsonProperty("kafkaProducerProperties") Map<String, String> kafkaProducerProperties
    ) {
        this.topic = topic; // Can be null, validation is done by OutputTarget

        // Default partition key field
        this.partitionKeyField = (partitionKeyField == null || partitionKeyField.isBlank()) ? "pipedocId" : partitionKeyField;

        // Default compression (prefer snappy for performance)
        this.compressionType = (compressionType == null || compressionType.isBlank()) ? "snappy" : compressionType;

        // Default batch size (16KB)
        this.batchSize = (batchSize == null || batchSize <= 0) ? 16384 : batchSize;

        // Default linger ms (10ms for low latency)
        this.lingerMs = (lingerMs == null || lingerMs < 0) ? 10 : lingerMs;

        this.kafkaProducerProperties = (kafkaProducerProperties == null) ? Collections.emptyMap() : Map.copyOf(kafkaProducerProperties);
    }

    /**
     * Derives the DLQ topic name from the main topic.
     * Following the Seinfeld "good luck with all that" pattern - if they want custom DLQ topics,
     * they can add them to the whitelist and maintain them themselves.
     */
    @JsonIgnore
    public String getDlqTopic() {
        return topic != null ? topic + ".dlq" : null;
    }

    /**
     * Gets all producer properties including the explicit fields.
     * This merges the explicit fields with any additional properties.
     */
    @JsonIgnore
    public Map<String, String> getAllProducerProperties() {
        Map<String, String> allProps = new java.util.HashMap<>(kafkaProducerProperties);
        allProps.put("compression.type", compressionType);
        allProps.put("batch.size", String.valueOf(batchSize));
        allProps.put("linger.ms", String.valueOf(lingerMs));
        // Note: acks is app-controlled and should be set at the engine level
        return Collections.unmodifiableMap(allProps);
    }
}
