package com.rokkon.pipeline.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.validators.IntraPipelineLoopValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public abstract class IntraPipelineLoopValidatorTestBase {
    
    protected abstract IntraPipelineLoopValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        IntraPipelineLoopValidator validator = getValidator();
        assertEquals(600, validator.getPriority());
        assertEquals("IntraPipelineLoopValidator", validator.getValidatorName());
    }
    
    @Test
    void testNullPipelineConfiguration() {
        ValidationResult result = getValidator().validate(null);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testEmptyPipelineSteps() {
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Collections.emptyMap()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testSingleStep() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Single processor",
            null, null,
            Collections.emptyList(),
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
        assertTrue(result.warnings().get(0).contains("Intra-pipeline loop detection is not yet implemented"));
    }
    
    @Test
    void testLinearPipeline() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        // Create a linear pipeline: step1 -> step2 -> step3
        KafkaTransportConfig kafka1 = new KafkaTransportConfig(
            "pipeline.step2.input",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output1 = new PipelineStepConfig.OutputTarget(
            "step2",
            TransportType.KAFKA,
            null,
            kafka1
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "step1",
            StepType.CONNECTOR,
            "First step",
            null, null,
            Collections.emptyList(),
            Map.of("default", output1),
            null, null, null, null, null,
            processorInfo
        );
        
        KafkaInputDefinition input2 = new KafkaInputDefinition(
            Collections.singletonList("pipeline.step2.input"),
            "consumer-group",
            null
        );
        
        KafkaTransportConfig kafka2 = new KafkaTransportConfig(
            "pipeline.step3.input",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output2 = new PipelineStepConfig.OutputTarget(
            "step3",
            TransportType.KAFKA,
            null,
            kafka2
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
            "step2",
            StepType.PIPELINE,
            "Second step",
            null, null,
            Collections.singletonList(input2),
            Map.of("default", output2),
            null, null, null, null, null,
            processorInfo
        );
        
        KafkaInputDefinition input3 = new KafkaInputDefinition(
            Collections.singletonList("pipeline.step3.input"),
            "consumer-group",
            null
        );
        
        PipelineStepConfig step3 = new PipelineStepConfig(
            "step3",
            StepType.SINK,
            "Third step",
            null, null,
            Collections.singletonList(input3),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2, "step3", step3)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Intra-pipeline loop detection is not yet implemented"));
    }
    
    // TODO: Add tests for actual loop detection when implemented
    // For now, these tests just verify the validator doesn't crash
    // and returns the expected warning about incomplete implementation
}