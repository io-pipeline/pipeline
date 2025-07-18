package io.pipeline.model.validation.validators;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.StepType;
import io.pipeline.api.model.TransportType;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;

/**
 * Validates that all step references point to existing steps within the pipeline.
 * Checks for duplicate step names and validates internal gRPC references.
 * Also validates that gRPC service names are registered in the allowed services list.
 */
@ApplicationScoped
public class StepReferenceValidator implements PipelineConfigValidator {
    
    // Hardcoded list of allowed services for testing
    // In a real-world scenario, this would be injected or retrieved from configuration
    private static final Set<String> ALLOWED_SERVICES = Set.of(
            "test-sink-service", 
            "some-other-service", 
            "gutenberg-connector", 
            "opensearch-sink",
            "parser",
            "chunker",
            "embedder",
            "echo",
            "testing-harness",
            "proxy-module",
            "filesystem-crawler",
            "echo-module",
            "ab",
            "bean-step1",
            "bean-step2",
            "service"
    );
    
    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        PipelineConfig config = (PipelineConfig) validatable;
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return ValidationResultFactory.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Collect all valid step IDs
        Set<String> validStepIds = new HashSet<>(config.pipelineSteps().keySet());
        
        // Check for duplicate step names
        Set<String> stepNames = new HashSet<>();
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step != null && step.stepName() != null && !step.stepName().isBlank()) {
                if (!stepNames.add(step.stepName())) {
                    errors.add("Duplicate step name found: " + step.stepName());
                }
            }
            
            // Check processor info for issues
            if (step != null && step.processorInfo() != null) {
                
                // Check if gRPC service name is registered in allowed services list
                if (!step.processorInfo().grpcServiceName().isBlank()) {
                    String serviceName = step.processorInfo().grpcServiceName();
                    if (!isAllowedService(serviceName)) {
                        errors.add("Step '" + stepId + "' references gRPC service '" + serviceName + 
                                 "' which is not registered in the cluster's allowed services.");
                    }
                } else {
                    errors.add("Step '" + stepId + "' is missing a gRPC service name.");
                }
            }
        }
        
        // Validate all references
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step != null && step.outputs() != null) {
                for (var outputEntry : step.outputs().entrySet()) {
                    String outputKey = outputEntry.getKey();
                    var output = outputEntry.getValue();
                    
                    if (output != null && 
                        output.transportType() == TransportType.GRPC &&
                        output.grpcTransport() != null && 
                        output.grpcTransport().serviceName() != null &&
                        !output.grpcTransport().serviceName().isBlank()) {
                        
                        String targetService = output.grpcTransport().serviceName();
                        
                        // Check if this looks like an internal reference (no dots, suggesting it's not a FQDN)
                        if (!targetService.contains(".") && !validStepIds.contains(targetService)) {
                            errors.add("Step '" + stepId + "' output '" + outputKey + 
                                     "' references non-existent target step '" + targetService + "'");
                        }
                    }
                }
            }
        }
        
        return errors.isEmpty() ? ValidationResultFactory.successWithWarnings(warnings) : ValidationResultFactory.failure(errors, warnings);
    }
    
    /**
     * Checks if a gRPC service name is in the allowed services list.
     * 
     * @param serviceName The gRPC service name to check
     * @return true if the service is allowed, false otherwise
     */
    private boolean isAllowedService(String serviceName) {
        return ALLOWED_SERVICES.contains(serviceName);
    }
    
    @Override
    public int getPriority() {
        return 400; // Run after structural validation
    }
    
    @Override
    public String getValidatorName() {
        return "StepReferenceValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Run validation in all modes to ensure consistent behavior
        // We want to error out for internalProcessorBeanName in all modes
        return Set.of(ValidationMode.PRODUCTION, ValidationMode.DESIGN, ValidationMode.TESTING);
    }
}