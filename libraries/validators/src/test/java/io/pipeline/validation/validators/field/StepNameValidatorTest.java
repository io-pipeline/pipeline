package io.pipeline.validation.validators.field;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.StepType;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.model.validation.validators.field.StepNameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the StepNameValidator class.
 */
public class StepNameValidatorTest {

    private StepNameValidator validator;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        validator = new StepNameValidator(objectMapper);
    }
    
    @Test
    public void testCanHandle() {
        // Should handle PipelineConfig
        assertTrue(validator.canHandle(new PipelineConfig("test", Collections.emptyMap())));
        
        // Should not handle other types
        assertFalse(validator.canHandle(new TestValidatable()));
    }
    
    @Test
    public void testValidate_ValidStepNames() {
        // Create a pipeline with valid step names
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        PipelineStepConfig.ProcessorInfo processorInfo = 
                new PipelineStepConfig.ProcessorInfo("test-service", null);
        
        PipelineStepConfig step1 = new PipelineStepConfig(
                "step1", StepType.CONNECTOR, processorInfo);
        PipelineStepConfig step2 = new PipelineStepConfig(
                "step2", StepType.PIPELINE, processorInfo);
        
        steps.put("step1", step1);
        steps.put("step2", step2);
        
        PipelineConfig config = new PipelineConfig("test-pipeline", steps);
        
        // Validate
        List<String> suggestions = validator.validate(config, Collections.emptySet(), 0);
        
        // Should not have any suggestions for valid step names
        assertTrue(suggestions.isEmpty());
    }
    
    @Test
    public void testValidate_StepIdMismatch() {
        // Create a pipeline with step ID not matching stepName
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        PipelineStepConfig.ProcessorInfo processorInfo = 
                new PipelineStepConfig.ProcessorInfo("test-service", null);
        
        PipelineStepConfig step = new PipelineStepConfig(
                "different-name", StepType.CONNECTOR, processorInfo);
        
        steps.put("step1", step);
        
        PipelineConfig config = new PipelineConfig("test-pipeline", steps);
        
        // Validate
        List<String> suggestions = validator.validate(config, Collections.emptySet(), 0);
        
        // Should suggest fixing the mismatch
        assertEquals(1, suggestions.size());
        assertTrue(suggestions.get(0).contains("Step ID 'step1' should match stepName 'different-name'"));
    }
    
    @Test
    public void testValidate_InvalidStepName() {
        // Create a pipeline with invalid step name
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        PipelineStepConfig.ProcessorInfo processorInfo = 
                new PipelineStepConfig.ProcessorInfo("test-service", null);
        
        // Invalid step name with special characters
        PipelineStepConfig step = new PipelineStepConfig(
                "invalid@step", StepType.CONNECTOR, processorInfo);
        
        steps.put("invalid@step", step);
        
        PipelineConfig config = new PipelineConfig("test-pipeline", steps);
        
        // Create some validation errors
        Set<ValidationMessage> errors = new HashSet<>();
        
        // Validate
        List<String> suggestions = validator.validate(config, errors, 0);
        
        // Should suggest fixing the invalid step name
        assertEquals(3, suggestions.size());
        assertTrue(suggestions.get(0).contains("doesn't match required pattern"));
        assertTrue(suggestions.get(0).contains("Suggested fix:"));
    }
    
    @Test
    public void testGetPriority() {
        // Should have the expected priority
        assertEquals(90, validator.getPriority());
    }
    
    // Simple implementation of PipelineConfigValidatable for testing
    private static class TestValidatable implements PipelineConfigValidatable {
    }
}