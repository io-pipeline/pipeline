package io.pipeline.model.validation;

import io.pipeline.api.validation.ConfigValidator;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;

import java.util.List;

/**
 * A mode-aware implementation of PipelineConfigValidator that delegates to a CompositeValidator
 * with a specific ValidationMode.
 * 
 * This class allows creating validators for specific modes (PRODUCTION, DESIGN, TESTING)
 * that will only run validators that support that mode and with appropriate strictness.
 */
public class ModePipelineConfigValidator implements PipelineConfigValidator {
    
    private final CompositeValidator<PipelineConfigValidatable> composite;
    private final ValidationMode mode;
    
    /**
     * Creates a new ModePipelineConfigValidator with the specified validators and mode.
     * 
     * @param validators The list of validators to use
     * @param mode The validation mode to use
     */
    public ModePipelineConfigValidator(List<ConfigValidator<PipelineConfigValidatable>> validators, ValidationMode mode) {
        this.composite = new CompositeValidator<>(mode.name() + " Pipeline Configuration Validator", validators);
        this.mode = mode;
    }
    
    @Override
    public ValidationResult validate(PipelineConfigValidatable config) {
        // Use the CompositeValidator's mode-aware validation
        return composite.validate(config, mode);
    }
    
    @Override
    public String getValidatorName() {
        return mode.name() + "PipelineConfigValidator";
    }
    
    @Override
    public int getPriority() {
        return 0;
    }
}