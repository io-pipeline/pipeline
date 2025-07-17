package io.pipeline.model.validation;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.ConfigValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * CDI producer for CompositeValidator instances.
 * This solves the "no valid bean constructor" issue by providing
 * properly configured validators for injection.
 */
@ApplicationScoped
public class ValidatorProducer {
    
    private static final Logger LOG = Logger.getLogger(ValidatorProducer.class);
    
    @Inject
    Instance<ConfigValidator<PipelineConfig>> pipelineValidators;
    
    @Inject
    Instance<ConfigValidator<PipelineClusterConfig>> clusterValidators;
    
    /**
     * Produces the default CompositeValidator for PipelineConfig validation.
     * This validator respects the supportedModes() method of each validator.
     */
    @Produces
    @ApplicationScoped
    public CompositeValidator<PipelineConfig> pipelineConfigValidator() {
        List<ConfigValidator<PipelineConfig>> validators = new ArrayList<>();
        
        // Add all discovered validators
        for (ConfigValidator<PipelineConfig> validator : pipelineValidators) {
            // Skip the composite validator itself to avoid circular dependency
            if (!(validator instanceof CompositeValidator)) {
                validators.add(validator);
                LOG.debugf("Added pipeline validator: %s (supports modes: %s)", 
                    validator.getValidatorName(), validator.supportedModes());
            }
        }
        
        LOG.infof("Created CompositeValidator for PipelineConfig with %d validators", validators.size());
        return new CompositeValidator<>("PipelineConfigComposite", validators);
    }
    
    /**
     * Produces a CompositeValidator for PipelineClusterConfig validation.
     * Automatically discovers and includes all available PipelineClusterConfig validators.
     */
    @Produces
    @ApplicationScoped
    public CompositeValidator<PipelineClusterConfig> clusterConfigValidator() {
        List<ConfigValidator<PipelineClusterConfig>> validators = new ArrayList<>();
        
        // Add all discovered validators
        for (ConfigValidator<PipelineClusterConfig> validator : clusterValidators) {
            // Skip the composite validator itself to avoid circular dependency
            if (!(validator instanceof CompositeValidator)) {
                validators.add(validator);
                LOG.debugf("Added cluster validator: %s", validator.getValidatorName());
            }
        }
        
        LOG.infof("Created CompositeValidator for PipelineClusterConfig with %d validators", validators.size());
        return new CompositeValidator<>("PipelineClusterConfigComposite", validators);
    }
}