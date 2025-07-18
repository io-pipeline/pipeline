package io.pipeline.validation.validators;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.StepType;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.validators.ProcessorInfoValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public abstract class ProcessorInfoValidatorTestBase {
    
    protected abstract ProcessorInfoValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        ProcessorInfoValidator validator = getValidator();
        assertEquals(250, validator.getPriority());
        assertEquals("ProcessorInfoValidator", validator.getValidatorName());
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
    void testValidGrpcServiceName() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
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
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testValidInternalBeanName() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "documentParserBean"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
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
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testShortGrpcServiceName() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "ab" // Too short
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
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
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("gRPC service name 'ab' is too short"));
    }
    
    @Test
    void testLongGrpcServiceName() {
        String longName = "a".repeat(101);
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            longName
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
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
        assertTrue(result.warnings().getFirst().contains("gRPC service name") && result.warnings().getFirst().contains("is very long"));
    }
    
    @Test
    void testInvalidGrpcServiceNameFormat() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "123-invalid-start"// Starts with number
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
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
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("should start with a letter"));
    }
    
    @Test
    void testLocalhostWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "localhost:8080"
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
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
        assertTrue(result.warnings().getFirst().contains("localhost reference"));
    }
    
    
    @Test
    void testMultipleStepsWithMixedIssues() {
        // Valid processor
        PipelineStepConfig.ProcessorInfo validProcessor = new PipelineStepConfig.ProcessorInfo(
            "document-parser.service.com"
        );
        
        // Invalid gRPC name
        PipelineStepConfig.ProcessorInfo invalidGrpc = new PipelineStepConfig.ProcessorInfo(
            "@invalid"
        );
        
        // Valid bean name (bean name validation has been removed)
        PipelineStepConfig.ProcessorInfo validBean = new PipelineStepConfig.ProcessorInfo(
            "processorBean"
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parser step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            validProcessor
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Processor step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            invalidGrpc
        );
        
        PipelineStepConfig step3 = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writer step",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            validBean
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2, "step3", step3)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertFalse(result.valid());
        assertEquals(1, result.errors().size()); // Only one invalid name (gRPC)
        assertTrue(result.warnings().isEmpty());
    }
}