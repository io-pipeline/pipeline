package io.pipeline.validation.validators.field;

import com.networknt.schema.ValidationMessage;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.model.validation.validators.field.FieldValidator;
import io.pipeline.model.validation.validators.field.FieldValidatorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the FieldValidatorRegistry class.
 */
public class FieldValidatorRegistryTest {

    // Simple implementation of PipelineConfigValidatable for testing
    private static class TestValidatable implements PipelineConfigValidatable {
        private final String type;
        
        public TestValidatable(String type) {
            this.type = type;
        }
        
        public String getType() {
            return type;
        }
    }
    
    // Simple implementation of FieldValidator for testing
    private static class TestValidator implements FieldValidator<PipelineConfigValidatable> {
        private final int priority;
        private final String type;
        
        public TestValidator(int priority, String type) {
            this.priority = priority;
            this.type = type;
        }
        
        @Override
        public List<String> validate(PipelineConfigValidatable validatable, Set<ValidationMessage> errors, int currentDepth) {
            return Collections.singletonList("Validation for " + ((TestValidatable)validatable).getType());
        }
        
        @Override
        public boolean canHandle(PipelineConfigValidatable validatable) {
            if (validatable instanceof TestValidatable) {
                return ((TestValidatable)validatable).getType().equals(type);
            }
            return false;
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
    }
    
    private FieldValidatorRegistry registry;
    private TestValidator highPriorityValidator;
    private TestValidator mediumPriorityValidator;
    private TestValidator lowPriorityValidator;
    
    @BeforeEach
    public void setUp() {
        // Create test validators with different priorities
        highPriorityValidator = new TestValidator(100, "high");
        mediumPriorityValidator = new TestValidator(50, "medium");
        lowPriorityValidator = new TestValidator(10, "low");
        
        // Create the registry with the test validators
        registry = new FieldValidatorRegistry(Arrays.asList(
                lowPriorityValidator, 
                mediumPriorityValidator, 
                highPriorityValidator
        ));
    }
    
    @Test
    public void testGetAllValidators() {
        // The registry should return all validators sorted by priority (highest first)
        List<FieldValidator<?>> allValidators = registry.getAllValidators();
        
        assertEquals(3, allValidators.size());
        assertEquals(highPriorityValidator, allValidators.get(0));
        assertEquals(mediumPriorityValidator, allValidators.get(1));
        assertEquals(lowPriorityValidator, allValidators.get(2));
    }
    
    @Test
    public void testGetValidatorsFor_MatchingType() {
        // Create a test validatable that matches the high priority validator
        TestValidatable validatable = new TestValidatable("high");
        
        // The registry should return only the high priority validator
        List<FieldValidator<?>> validators = registry.getValidatorsFor(validatable);
        
        assertEquals(1, validators.size());
        assertEquals(highPriorityValidator, validators.get(0));
    }
    
    @Test
    public void testGetValidatorsFor_NoMatch() {
        // Create a test validatable that doesn't match any validator
        TestValidatable validatable = new TestValidatable("unknown");
        
        // The registry should return an empty list
        List<FieldValidator<?>> validators = registry.getValidatorsFor(validatable);
        
        assertTrue(validators.isEmpty());
    }
    
    @Test
    public void testEmptyRegistry() {
        // Create an empty registry
        FieldValidatorRegistry emptyRegistry = new FieldValidatorRegistry(Collections.emptyList());
        
        // The registry should return an empty list for all methods
        assertTrue(emptyRegistry.getAllValidators().isEmpty());
        assertTrue(emptyRegistry.getValidatorsFor(new TestValidatable("any")).isEmpty());
    }
}