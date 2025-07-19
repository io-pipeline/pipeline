package io.pipeline.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.validators.InterPipelineLoopValidator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for InterPipelineLoopValidator.
 * Contains common test cases for detecting loops between pipelines.
 */
public abstract class InterPipelineLoopValidatorTestBase {
    
    protected abstract InterPipelineLoopValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        InterPipelineLoopValidator validator = getValidator();
        assertEquals(700, validator.getPriority());
        assertEquals("InterPipelineLoopValidator", validator.getValidatorName());
    }
    
    @Test
    void testNullClusterConfiguration() {
        ValidationResult result = getValidator().validate(null);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testNullPipelineGraphConfig() {
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            null,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testEmptyPipelines() {
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Collections.emptyMap()
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testSinglePipeline() {
        PipelineConfig pipeline = new PipelineConfig(
            "test-pipeline",
            Collections.emptyMap()
        );
        
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of("pipeline1", pipeline)
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testTwoPipelinesNoLoop() {
        // Pipeline A publishes to topicA
        // Pipeline B listens to topicA (A -> B)
        // Pipeline B publishes to topicB (which A does not listen to)
        
        // Create Pipeline A with a step that publishes to topicA
        PipelineStepConfig.ProcessorInfo processorInfoA = new PipelineStepConfig.ProcessorInfo("processor-a");
        
        KafkaTransportConfig kafkaTransportA = new KafkaTransportConfig(
            "topicA", "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        PipelineStepConfig.OutputTarget outputA = new PipelineStepConfig.OutputTarget(
            "step-b", TransportType.KAFKA, null, kafkaTransportA
        );
        
        PipelineStepConfig stepA = createStep(
            "step-a",
            StepType.PIPELINE,
            processorInfoA,
            Collections.emptyList(),
            Map.of("default", outputA)
        );
        
        PipelineConfig pipelineA = new PipelineConfig(
            "pipeline-a",
            Map.of("step-a", stepA)
        );
        
        // Create Pipeline B with a step that listens to topicA and publishes to topicB
        PipelineStepConfig.ProcessorInfo processorInfoB = new PipelineStepConfig.ProcessorInfo("processor-b");
        
        KafkaInputDefinition inputB = new KafkaInputDefinition(
            List.of("topicA"), "pipeline-b.consumer-group", Map.of()
        );
        
        KafkaTransportConfig kafkaTransportB = new KafkaTransportConfig(
            "topicB", "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        PipelineStepConfig.OutputTarget outputB = new PipelineStepConfig.OutputTarget(
            "some-step", TransportType.KAFKA, null, kafkaTransportB
        );
        
        PipelineStepConfig stepB = createStep(
            "step-b",
            StepType.PIPELINE,
            processorInfoB,
            List.of(inputB),
            Map.of("default", outputB)
        );
        
        PipelineConfig pipelineB = new PipelineConfig(
            "pipeline-b",
            Map.of("step-b", stepB)
        );
        
        // Create the cluster config with both pipelines
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of("pipeline-a", pipelineA, "pipeline-b", pipelineB)
        );
        
        PipelineClusterConfig clusterConfig = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(clusterConfig);
        assertTrue(result.valid(), "Two pipelines with A -> B flow should not have loops");
        assertTrue(result.errors().isEmpty(), "There should be no errors");
        assertTrue(result.warnings().isEmpty(), "There should be no warnings");
    }
    
    @Test
    void testTwoPipelinesWithDirectLoop() {
        // Pipeline A publishes to topicA, listens to topicB
        // Pipeline B publishes to topicB, listens to topicA
        // This creates a loop: A -> B -> A
        
        // Create Pipeline A with a step that publishes to topicA and listens to topicB
        PipelineStepConfig.ProcessorInfo processorInfoA = new PipelineStepConfig.ProcessorInfo("processor-a");
        
        KafkaInputDefinition inputA = new KafkaInputDefinition(
            List.of("topicB"), "pipeline-a.consumer-group", Map.of()
        );
        
        KafkaTransportConfig kafkaTransportA = new KafkaTransportConfig(
            "topicA", "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        PipelineStepConfig.OutputTarget outputA = new PipelineStepConfig.OutputTarget(
            "step-b", TransportType.KAFKA, null, kafkaTransportA
        );
        
        PipelineStepConfig stepA = createStep(
            "step-a",
            StepType.PIPELINE,
            processorInfoA,
            List.of(inputA),
            Map.of("default", outputA)
        );
        
        PipelineConfig pipelineA = new PipelineConfig(
            "pipeline-a",
            Map.of("step-a", stepA)
        );
        
        // Create Pipeline B with a step that listens to topicA and publishes to topicB
        PipelineStepConfig.ProcessorInfo processorInfoB = new PipelineStepConfig.ProcessorInfo("processor-b");
        
        KafkaInputDefinition inputB = new KafkaInputDefinition(
            List.of("topicA"), "pipeline-b.consumer-group", Map.of()
        );
        
        KafkaTransportConfig kafkaTransportB = new KafkaTransportConfig(
            "topicB", "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        PipelineStepConfig.OutputTarget outputB = new PipelineStepConfig.OutputTarget(
            "step-a", TransportType.KAFKA, null, kafkaTransportB
        );
        
        PipelineStepConfig stepB = createStep(
            "step-b",
            StepType.PIPELINE,
            processorInfoB,
            List.of(inputB),
            Map.of("default", outputB)
        );
        
        PipelineConfig pipelineB = new PipelineConfig(
            "pipeline-b",
            Map.of("step-b", stepB)
        );
        
        // Create the cluster config with both pipelines
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of("pipeline-a", pipelineA, "pipeline-b", pipelineB)
        );
        
        PipelineClusterConfig clusterConfig = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(clusterConfig);
        assertFalse(result.valid(), "Two pipelines with a direct loop should fail validation");
        assertFalse(result.errors().isEmpty(), "There should be at least one error");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Detected a loop across pipelines")), 
                "The error should indicate a loop was detected");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("pipeline-a:step-a") && e.contains("pipeline-b:step-b")),
                "The error should mention both pipelines involved in the loop");
    }
    
    @Test
    void testThreePipelinesWithLoop() {
        // Pipeline A publishes to topicA (to Pipeline B)
        // Pipeline B publishes to topicB (to Pipeline C)
        // Pipeline C publishes to topicC (to Pipeline A)
        // This creates a loop: A -> B -> C -> A
        
        // Create Pipeline A
        PipelineStepConfig.ProcessorInfo processorInfoA = new PipelineStepConfig.ProcessorInfo("processor-a");
        
        KafkaInputDefinition inputA = new KafkaInputDefinition(
            List.of("topicC"), "pipeline-a.consumer-group", Map.of()
        );
        
        KafkaTransportConfig kafkaTransportA = new KafkaTransportConfig(
            "topicA", "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        PipelineStepConfig.OutputTarget outputA = new PipelineStepConfig.OutputTarget(
            "step-b", TransportType.KAFKA, null, kafkaTransportA
        );
        
        PipelineStepConfig stepA = createStep(
            "step-a",
            StepType.PIPELINE,
            processorInfoA,
            List.of(inputA),
            Map.of("default", outputA)
        );
        
        PipelineConfig pipelineA = new PipelineConfig(
            "pipeline-a",
            Map.of("step-a", stepA)
        );
        
        // Create Pipeline B
        PipelineStepConfig.ProcessorInfo processorInfoB = new PipelineStepConfig.ProcessorInfo("processor-b");
        
        KafkaInputDefinition inputB = new KafkaInputDefinition(
            List.of("topicA"), "pipeline-b.consumer-group", Map.of()
        );
        
        KafkaTransportConfig kafkaTransportB = new KafkaTransportConfig(
            "topicB", "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        PipelineStepConfig.OutputTarget outputB = new PipelineStepConfig.OutputTarget(
            "step-c", TransportType.KAFKA, null, kafkaTransportB
        );
        
        PipelineStepConfig stepB = createStep(
            "step-b",
            StepType.PIPELINE,
            processorInfoB,
            List.of(inputB),
            Map.of("default", outputB)
        );
        
        PipelineConfig pipelineB = new PipelineConfig(
            "pipeline-b",
            Map.of("step-b", stepB)
        );
        
        // Create Pipeline C
        PipelineStepConfig.ProcessorInfo processorInfoC = new PipelineStepConfig.ProcessorInfo("processor-c");
        
        KafkaInputDefinition inputC = new KafkaInputDefinition(
            List.of("topicB"), "pipeline-c.consumer-group", Map.of()
        );
        
        KafkaTransportConfig kafkaTransportC = new KafkaTransportConfig(
            "topicC", "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        PipelineStepConfig.OutputTarget outputC = new PipelineStepConfig.OutputTarget(
            "step-a", TransportType.KAFKA, null, kafkaTransportC
        );
        
        PipelineStepConfig stepC = createStep(
            "step-c",
            StepType.PIPELINE,
            processorInfoC,
            List.of(inputC),
            Map.of("default", outputC)
        );
        
        PipelineConfig pipelineC = new PipelineConfig(
            "pipeline-c",
            Map.of("step-c", stepC)
        );
        
        // Create the cluster config with all three pipelines
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of("pipeline-a", pipelineA, "pipeline-b", pipelineB, "pipeline-c", pipelineC)
        );
        
        PipelineClusterConfig clusterConfig = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(clusterConfig);
        assertFalse(result.valid(), "Three pipelines with a loop should fail validation");
        assertFalse(result.errors().isEmpty(), "There should be at least one error");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Detected a loop across pipelines")), 
                "The error should indicate a loop was detected");
        assertTrue(result.errors().stream().anyMatch(e -> 
                e.contains("pipeline-a:step-a") && e.contains("pipeline-b:step-b") && e.contains("pipeline-c:step-c")),
                "The error should mention all three pipelines involved in the loop");
    }
    
    @Test
    void testPipelinePublishesAndListensToSameTopic() {
        // Pipeline A publishes to "shared-topic"
        // Pipeline A also listens to "shared-topic"
        // This creates a self-loop: A -> A
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo("processor-a");
        
        KafkaInputDefinition input = new KafkaInputDefinition(
            List.of("shared-topic"), "pipeline-a.consumer-group", Map.of()
        );
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "shared-topic", "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "step-a", TransportType.KAFKA, null, kafkaTransport
        );
        
        PipelineStepConfig step = createStep(
            "step-a",
            StepType.PIPELINE,
            processorInfo,
            List.of(input),
            Map.of("default", output)
        );
        
        PipelineConfig pipeline = new PipelineConfig(
            "pipeline-a",
            Map.of("step-a", step)
        );
        
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of("pipeline-a", pipeline)
        );
        
        PipelineClusterConfig clusterConfig = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(clusterConfig);
        assertFalse(result.valid(), "Pipeline publishing to and listening from the same topic should fail validation");
        assertFalse(result.errors().isEmpty(), "There should be at least one error");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Detected a loop across pipelines")), 
                "The error should indicate a loop was detected");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("pipeline-a:step-a -> pipeline-a:step-a")),
                "The error should mention the self-loop");
    }
    
    // Helper method to create a pipeline step
    private PipelineStepConfig createStep(String name, StepType type, PipelineStepConfig.ProcessorInfo processorInfo,
                                          List<KafkaInputDefinition> kafkaInputs,
                                          Map<String, PipelineStepConfig.OutputTarget> outputs) {
        return new PipelineStepConfig(
                name, type, "Test Step " + name, null,
                new PipelineStepConfig.JsonConfigOptions(null, Collections.emptyMap()),
                kafkaInputs != null ? kafkaInputs : Collections.emptyList(),
                outputs != null ? outputs : Collections.emptyMap(),
                3, 1000L, 30000L, 2.0, null,
                processorInfo
        );
    }
}