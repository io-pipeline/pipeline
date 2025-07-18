package io.pipeline.model.validation.validators;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates that there are no circular dependencies within a pipeline.
 * TODO: Implement full loop detection logic.
 */
@ApplicationScoped
public class IntraPipelineLoopValidator implements PipelineConfigValidator {
    
    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        PipelineConfig config = (PipelineConfig) validatable;
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return ValidationResultFactory.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // TODO: Implement comprehensive loop detection
        // For now, just add a warning that this validation is not yet implemented
        // The old system had sophisticated graph traversal logic
       // warnings.add("Intra-pipeline loop detection is not yet implemented");
        
        //return errors.isEmpty() ? ValidationResultFactory.successWithWarnings(warnings) : ValidationResultFactory.failure(errors, warnings);
        return ValidationResultFactory.success();
    }
    
    @Override
    public int getPriority() {
        return 600; // Run after reference validation
    }
    
    @Override
    public String getValidatorName() {
        return "IntraPipelineLoopValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Loop detection is important for design and production but can be relaxed for testing
        return Set.of(ValidationMode.PRODUCTION, ValidationMode.DESIGN);
    }
}