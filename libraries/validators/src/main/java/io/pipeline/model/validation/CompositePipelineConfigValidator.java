package io.pipeline.model.validation;



import io.pipeline.api.validation.ConfigValidator;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;

import java.util.List;

/**
 * Composite implementation of PipelineConfigValidator that delegates to a list of validators.
 * This class is not a CDI bean itself - it's created via PipelineValidatorProducer.
 */
public class CompositePipelineConfigValidator implements PipelineConfigValidator {

    private final CompositeValidator<PipelineConfigValidatable> composite;

    public CompositePipelineConfigValidator() {
        // Default constructor for CDI
        this.composite = new CompositeValidator<>("Pipeline Configuration Validator", List.of());
    }

    public CompositePipelineConfigValidator(List<ConfigValidator<PipelineConfigValidatable>> validators) {
        this.composite = new CompositeValidator<>("Pipeline Configuration Validator", validators);
    }

    @Override
    public ValidationResult validate(PipelineConfigValidatable config) {
        return composite.validate(config);
    }

    @Override
    public String getValidatorName() {
        return composite.getValidatorName();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    public void setValidators(List<ConfigValidator<PipelineConfigValidatable>> validators) {
        composite.getValidators().clear();
        composite.getValidators().addAll(validators);
    }
}