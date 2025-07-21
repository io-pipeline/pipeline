package io.pipeline.model.validation.validators;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.StepType;
import io.pipeline.api.validation.ModeAwareValidator;
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
 * Schema-based validator for pipeline configurations.
 * This validator enforces different rules based on the validation mode:
 * <p>
 * - DESIGN mode: Very lenient, only checks basic structure
 * - TESTING mode: Moderate validation, allows some missing fields
 * - PRODUCTION mode: Strict validation, all required fields must be present
 */
@ApplicationScoped
public class SchemaValidator implements ModeAwareValidator<PipelineConfig> {
    
    private static final Logger LOG = Logger.getLogger(SchemaValidator.class);
    
    @Inject
    ObjectMapper objectMapper;
    
    @Override
    public ValidationResult validate(PipelineConfig config, ValidationMode mode) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic structure validation (all modes)
        if (config == null) {
            errors.add("Pipeline configuration cannot be null");
            return ValidationResultFactory.failure(errors);
        }
        
        // Name validation
        if (config.name() == null || config.name().isBlank()) {
            if (mode == ValidationMode.DESIGN) {
                warnings.add("Pipeline name is missing - required before deployment");
            } else {
                errors.add("Pipeline name is required");
            }
        } else if (!isValidName(config.name())) {
            errors.add("Pipeline name contains invalid characters. Use only letters, numbers, hyphens, and underscores");
        }
        
        // Pipeline steps validation
        if (config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            if (mode == ValidationMode.DESIGN) {
                warnings.add("No pipeline steps defined yet");
            } else {
                errors.add("Pipeline must have at least one step");
            }
        } else {
            // Validate each step
            for (var entry : config.pipelineSteps().entrySet()) {
                String stepId = entry.getKey();
                PipelineStepConfig step = entry.getValue();
                
                validateStep(stepId, step, mode, errors, warnings);
            }
            
            // Check for required step types in production mode
            if (mode == ValidationMode.PRODUCTION) {
                validateStepTypes(config, errors, warnings);
            }
        }
        
        if (!errors.isEmpty()) {
            return ValidationResultFactory.failure(errors, warnings);
        } else if (!warnings.isEmpty()) {
            return ValidationResultFactory.successWithWarnings(warnings);
        } else {
            return ValidationResultFactory.success();
        }
    }
    
    private void validateStep(String stepId, PipelineStepConfig step, ValidationMode mode, 
                            List<String> errors, List<String> warnings) {
        String prefix = "Step '" + stepId + "': ";
        
        // Step name validation
        if (step.stepName() == null || step.stepName().isBlank()) {
            errors.add(prefix + "Step name is required");
        }
        
        // Step type validation
        if (step.stepType() == null) {
            errors.add(prefix + "Step type is required");
        }
        
        // Processor info validation
        if (step.processorInfo() == null) {
            if (mode == ValidationMode.DESIGN) {
                warnings.add(prefix + "Processor not configured yet");
            } else {
                errors.add(prefix + "Processor information is required");
            }
        } else {
            // Validate processor info has either gRPC service or bean name
            boolean hasGrpc = !step.processorInfo().grpcServiceName().isBlank();
            
            if (!hasGrpc) {
                if (mode == ValidationMode.DESIGN) {
                    warnings.add(prefix + "Processor type not selected (gRPC service)");
                } else {
                    errors.add(prefix + "Processor must specify either gRPC service");
                }
            }
        }
        
        // Output validation for non-SINK steps
        if (step.stepType() != null && step.stepType() != StepType.SINK) {
            if (step.outputs() == null || step.outputs().isEmpty()) {
                if (mode == ValidationMode.DESIGN || mode == ValidationMode.TESTING) {
                    warnings.add(prefix + "Non-SINK steps should have at least one output");
                } else {
                    errors.add(prefix + "Non-SINK steps must have at least one output in PRODUCTION mode");
                }
            }
        }
        
        // Custom config validation if schema ID is provided
        if (step.customConfigSchemaId() != null && !step.customConfigSchemaId().isBlank()) {
            if (step.customConfig() == null || 
                (step.customConfig().jsonConfig() == null && 
                 (step.customConfig().configParams() == null || step.customConfig().configParams().isEmpty()))) {
                warnings.add(prefix + "Schema ID specified but no configuration provided");
            }
        }
        
        // Retry configuration validation
        validateRetryConfig(prefix, step, mode, errors, warnings);
    }
    
    private void validateRetryConfig(String prefix, PipelineStepConfig step, ValidationMode mode,
                                   List<String> errors, List<String> warnings) {
        if (step.maxRetries() != null && step.maxRetries() < 0) {
            errors.add(prefix + "Max retries cannot be negative");
        }
        
        if (step.retryBackoffMs() != null && step.retryBackoffMs() < 0) {
            errors.add(prefix + "Retry backoff cannot be negative");
        }
        
        if (step.maxRetryBackoffMs() != null && step.maxRetryBackoffMs() < 0) {
            errors.add(prefix + "Max retry backoff cannot be negative");
        }
        
        if (step.retryBackoffMultiplier() != null && step.retryBackoffMultiplier() <= 0) {
            errors.add(prefix + "Retry backoff multiplier must be positive");
        }
        
        if (step.stepTimeoutMs() != null && step.stepTimeoutMs() <= 0) {
            errors.add(prefix + "Step timeout must be positive");
        }
        
        // Logical validation
        if (step.retryBackoffMs() != null && step.maxRetryBackoffMs() != null && 
            step.retryBackoffMs() > step.maxRetryBackoffMs()) {
            warnings.add(prefix + "Initial retry backoff is greater than max retry backoff");
        }
    }
    
    private void validateStepTypes(PipelineConfig config, List<String> errors, List<String> warnings) {
        boolean hasInitial = false;
        boolean hasRegular = false;
        
        for (PipelineStepConfig step : config.pipelineSteps().values()) {
            if (step.stepType() == StepType.CONNECTOR) {
                hasInitial = true;
            } else if (step.stepType() == StepType.PIPELINE) {
                hasRegular = true;
            }
        }
        
        if (!hasInitial) {
            // TODO: Change tests to reflect that pipelines can have inputs from Kafka without CONNECTOR steps
            // This should be a warning, not an error, since pipelines can receive inputs from Kafka
            warnings.add("Pipeline has no CONNECTOR step - ensure input comes from Kafka or external source");
        }
    }
    
    private boolean isValidName(String name) {
        // Allow letters, numbers, hyphens, and underscores
        return name.matches("^[a-zA-Z0-9_-]+$");
    }
    
    @Override
    public String getValidatorName() {
        return "SchemaValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // This validator runs in all modes but with different rules
        return Set.of(ValidationMode.DESIGN, ValidationMode.TESTING, ValidationMode.PRODUCTION);
    }
}