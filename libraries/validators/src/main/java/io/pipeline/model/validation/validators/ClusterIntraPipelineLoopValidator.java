package io.pipeline.model.validation.validators;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
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
import java.util.Map;
import java.util.Set;

/**
 * Validates that there are no circular dependencies within any pipeline in the cluster.
 * This is a cluster-level validator that applies the IntraPipelineLoopValidator to each pipeline.
 */
@ApplicationScoped
public class ClusterIntraPipelineLoopValidator implements PipelineClusterConfigValidator {
    
    private static final Logger LOG = Logger.getLogger(ClusterIntraPipelineLoopValidator.class);
    
    @Inject
    IntraPipelineLoopValidator intraPipelineLoopValidator;
    
    @Override
    public ValidationResult validate(PipelineClusterConfigValidatable validatable) {
        PipelineClusterConfig clusterConfig = (PipelineClusterConfig) validatable;
        if (clusterConfig == null || 
            clusterConfig.pipelineGraphConfig() == null || 
            clusterConfig.pipelineGraphConfig().pipelines() == null) {
            return ValidationResultFactory.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Get all pipelines from the cluster
        Map<String, PipelineConfig> pipelines = clusterConfig.pipelineGraphConfig().pipelines();
        
        // Apply the IntraPipelineLoopValidator to each pipeline
        for (Map.Entry<String, PipelineConfig> entry : pipelines.entrySet()) {
            String pipelineName = entry.getKey();
            PipelineConfig pipelineConfig = entry.getValue();
            
            LOG.debug("Validating intra-pipeline loops for pipeline: " + pipelineName);
            
            // Validate the pipeline using the IntraPipelineLoopValidator
            ValidationResult pipelineResult = intraPipelineLoopValidator.validate(pipelineConfig);
            
            // Add pipeline name prefix to each error and warning for better context
            for (String error : pipelineResult.errors()) {
                errors.add("Pipeline '" + pipelineName + "': " + error);
            }
            
            for (String warning : pipelineResult.warnings()) {
                warnings.add("Pipeline '" + pipelineName + "': " + warning);
            }
        }
        
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
        return 600; // Same priority as IntraPipelineLoopValidator
    }
    
    @Override
    public String getValidatorName() {
        return "ClusterIntraPipelineLoopValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Use the same supported modes as IntraPipelineLoopValidator
        return intraPipelineLoopValidator.supportedModes();
    }
}