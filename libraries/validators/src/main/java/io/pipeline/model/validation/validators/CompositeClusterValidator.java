package io.pipeline.model.validation.validators;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.validation.ConfigValidator;
import io.pipeline.api.validation.PipelineClusterConfigValidatable;
import io.pipeline.api.validation.PipelineClusterConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A composite validator that combines the results of InterPipelineLoopValidator and ClusterIntraPipelineLoopValidator.
 * This validator is used for cluster-level validation in tests.
 */
@ApplicationScoped
public class CompositeClusterValidator implements PipelineClusterConfigValidator {
    
    private static final Logger LOG = Logger.getLogger(CompositeClusterValidator.class);
    
    @Inject
    InterPipelineLoopValidator interPipelineLoopValidator;
    
    @Inject
    ClusterIntraPipelineLoopValidator clusterIntraPipelineLoopValidator;
    
    @Override
    public ValidationResult validate(PipelineClusterConfigValidatable validatable) {
        PipelineClusterConfig clusterConfig = (PipelineClusterConfig) validatable;
        if (clusterConfig == null) {
            return ValidationResultFactory.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate using InterPipelineLoopValidator
        ValidationResult interResult = interPipelineLoopValidator.validate(clusterConfig);
        errors.addAll(interResult.errors());
        warnings.addAll(interResult.warnings());
        
        // Validate using ClusterIntraPipelineLoopValidator
        ValidationResult intraResult = clusterIntraPipelineLoopValidator.validate(clusterConfig);
        errors.addAll(intraResult.errors());
        warnings.addAll(intraResult.warnings());
        
        // Return the combined validation result
        if (errors.isEmpty()) {
            return warnings.isEmpty() ? 
                ValidationResultFactory.success() : 
                ValidationResultFactory.successWithWarnings(warnings);
        } else {
            return ValidationResultFactory.failure(errors, warnings);
        }
    }
    
    @Override
    public int getPriority() {
        return 50; // Higher priority than individual validators
    }
    
    @Override
    public String getValidatorName() {
        return "CompositeClusterValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Support all modes
        return Set.of(ValidationMode.PRODUCTION, ValidationMode.DESIGN, ValidationMode.TESTING);
    }
}