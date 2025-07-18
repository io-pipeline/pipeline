package io.pipeline.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.validators.TransportConfigValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public abstract class TransportConfigValidatorTestBase {
    
    protected abstract TransportConfigValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        TransportConfigValidator validator = getValidator();
        assertEquals(350, validator.getPriority());
        assertEquals("TransportConfigValidator", validator.getValidatorName());
    }
    
    @Test
    void testNullPipelineConfiguration() {
        ValidationResult result = getValidator().validate(null);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testValidKafkaInput() {
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "test-consumer-group",
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testKafkaInputWithBlankConsumerGroup() {
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "", // Blank consumer group
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Consumer group ID is blank")));
    }
    
    @Test
    void testKafkaInputManyTopicsWarning() {
        List<String> manyTopics = List.of(
            "topic1", "topic2", "topic3", "topic4", "topic5",
            "topic6", "topic7", "topic8", "topic9", "topic10", "topic11"
        );
        
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            manyTopics,
            "test-consumer-group",
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Subscribing to many topics")));
    }
    
    @Test
    void testValidKafkaTransport() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            "pipedocId", // partitionKeyField
            "snappy", // compressionType
            16384, // batchSize
            10, // lingerMs
            null // kafkaProducerProperties
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testKafkaTransportLargeBatchSize() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            null,
            null,
            2000000, // Very large batch size (2MB)
            null,
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Very large batch size")));
    }
    
    @Test
    void testKafkaTransportHighLingerMs() {
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "output-topic",
            null,
            null,
            null,
            2000, // High linger ms
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("High linger ms")));
    }
    
    @Test
    void testValidGrpcTransport() {
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "document-processor",
            Map.of("timeout", "5000") // 5 second timeout
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "processor",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testGrpcTransportShortTimeout() {
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "document-processor",
            Map.of("timeout", "50") // Very short
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "processor",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Test step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Very short timeout")));
    }
    
    // Note: Transport type mismatch test removed as the model enforces this validation
    // The OutputTarget constructor throws IllegalArgumentException if transport type doesn't match config
    
    @Test
    void testKafkaConfigValidation() {
        Map<String, String> kafkaConsumerProps = Map.of(
            "max.poll.records", "5001", // Very high max poll records
            "session.timeout.ms", "3000" // Very short session timeout
        );
        
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "consumer-group",
            kafkaConsumerProps
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Test step",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Very high max.poll.records")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("session.timeout.ms less than 6 seconds")));
    }
}