package io.pipeline.model.validation;

import io.pipeline.api.validation.ConfigValidator;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;

import java.util.List;

/**
 * A concrete implementation of PipelineConfigValidator specifically for TESTING mode.
 * 
 * This class hardcodes the ValidationMode to TESTING, making it explicit and
 * easier to understand which validation mode is being used.
 */
public class TestingPipelineConfigValidator implements PipelineConfigValidator {
    
    private final CompositeValidator<PipelineConfigValidatable> composite;
    
    /**
     * Creates a new TestingPipelineConfigValidator with the specified validators.
     * 
     * @param validators The list of validators to use
     */
    public TestingPipelineConfigValidator(List<ConfigValidator<PipelineConfigValidatable>> validators) {
        this.composite = new CompositeValidator<>("Testing Pipeline Configuration Validator", validators);
    }
    
    @Override
    public ValidationResult validate(PipelineConfigValidatable config) {
        // Use the CompositeValidator's mode-aware validation with TESTING mode
        return composite.validate(config, ValidationMode.TESTING);
    }
    
    @Override
    public String getValidatorName() {
        return "TestingPipelineConfigValidator";
    }
    
    @Override
    public int getPriority() {
        return 0;
    }
}