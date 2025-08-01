package io.pipeline.model.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates output routing configuration in pipeline steps.
 * Ensures that output routes are properly configured and referenced steps exist.
 */
@ApplicationScoped
public class OutputRoutingValidator implements PipelineConfigValidator {

    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        PipelineConfig config = (PipelineConfig) validatable;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (config == null || config.pipelineSteps() == null) {
            errors.add("[" + getValidatorName() + "] Pipeline configuration or steps cannot be null");
            return ValidationResultFactory.failure(errors, warnings);
        }

        Set<String> stepIds = config.pipelineSteps().keySet();

        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            validateStepOutputs(stepId, step, stepIds, errors, warnings);
        }

        return errors.isEmpty() ? 
            (warnings.isEmpty() ? ValidationResultFactory.success() : ValidationResultFactory.successWithWarnings(warnings)) :
            ValidationResultFactory.failure(errors, warnings);
    }

    private void validateStepOutputs(String stepId, PipelineStepConfig step, 
                                   Set<String> allStepIds, List<String> errors, List<String> warnings) {
        if (step == null || step.outputs() == null) {
            return;
        }

        String prefix = String.format("Step '%s'", stepId);

        // Check if step has outputs defined
        if (step.outputs().isEmpty() && step.stepType() != StepType.SINK) {
            warnings.add(String.format("[%s] %s: No outputs defined for non-SINK step", getValidatorName(), prefix));
        }

        // SINK steps should not have outputs
        if (step.stepType() == StepType.SINK && !step.outputs().isEmpty()) {
            errors.add(String.format("[%s] %s: SINK steps should not have outputs", getValidatorName(), prefix));
        }

        // Validate each output
        for (var outputEntry : step.outputs().entrySet()) {
            String outputName = outputEntry.getKey();
            PipelineStepConfig.OutputTarget output = outputEntry.getValue();
            
            validateOutput(prefix, outputName, output, allStepIds, errors, warnings);
        }

        // Check for duplicate output names
        validateDuplicateOutputs(prefix, step, errors);

        // Warn if only one output but it's not named "default"
        if (step.outputs().size() == 1 && !step.outputs().containsKey("default")) {
            warnings.add(String.format("[%s] %s: Single output should be named 'default' for clarity", getValidatorName(), prefix));
        }
    }

    private void validateOutput(String stepPrefix, String outputName, 
                               PipelineStepConfig.OutputTarget output,
                               Set<String> allStepIds, List<String> errors, List<String> warnings) {
        if (output == null) {
            errors.add(String.format("[%s] %s output '%s': Output target cannot be null", getValidatorName(), stepPrefix, outputName));
            return;
        }

        String outputPrefix = String.format("%s output '%s'", stepPrefix, outputName);

        // Validate target step exists (if specified)
        if (!output.targetStepName().isEmpty()) {
            if (!allStepIds.contains(output.targetStepName())) {
                errors.add(String.format("[%s] %s: Target step '%s' does not exist in pipeline", 
                          getValidatorName(), outputPrefix, output.targetStepName()));
            }
        }

        // Validate transport type
        if (output.transportType() == null) {
            errors.add(String.format("[%s] %s: Transport type must be specified", getValidatorName(), outputPrefix));
            return;
        }

        // Validate transport configuration matches transport type
        switch (output.transportType()) {
            case KAFKA:
                if (output.kafkaTransport() == null) {
                    errors.add(String.format("[%s] %s: Kafka transport config required for KAFKA transport type", 
                              getValidatorName(), outputPrefix));
                } else {
                    validateKafkaTransport(outputPrefix, output.kafkaTransport(), errors, warnings);
                }
                if (output.grpcTransport() != null) {
                    warnings.add(String.format("[%s] %s: gRPC config specified but transport type is KAFKA", 
                                getValidatorName(), outputPrefix));
                }
                break;
                
            case GRPC:
                if (output.grpcTransport() == null) {
                    errors.add(String.format("[%s] %s: gRPC transport config required for GRPC transport type", 
                              getValidatorName(), outputPrefix));
                } else {
                    validateGrpcTransport(outputPrefix, output.grpcTransport(), errors, warnings);
                }
                if (output.kafkaTransport() != null) {
                    warnings.add(String.format("[%s] %s: Kafka config specified but transport type is GRPC", 
                                getValidatorName(), outputPrefix));
                }
                break;
        }
    }

    private void validateKafkaTransport(String prefix, KafkaTransportConfig config,
                                      List<String> errors, List<String> warnings) {
        if (config.topic() == null || config.topic().isEmpty()) {
            errors.add(String.format("[%s] %s: Kafka topic must be specified", getValidatorName(), prefix));
        }

        // Validate batch settings
        if (config.batchSize() <= 0) {
            errors.add(String.format("[%s] %s: Batch size must be positive (was %d)", getValidatorName(), prefix, config.batchSize()));
        }

        if (config.lingerMs() < 0) {
            errors.add(String.format("[%s] %s: Linger ms cannot be negative (was %d)", getValidatorName(), prefix, config.lingerMs()));
        }
    }

    private void validateGrpcTransport(String prefix, GrpcTransportConfig config,
                                     List<String> errors, List<String> warnings) {
        if (config.serviceName() == null || config.serviceName().isEmpty()) {
            errors.add(String.format("[%s] %s: gRPC service name must be specified", getValidatorName(), prefix));
        }

        // GrpcTransportConfig only has serviceName and grpcClientProperties
        // Validate timeout if specified in properties
        if (config.grpcClientProperties() != null && config.grpcClientProperties().containsKey("timeout")) {
            String timeoutStr = config.grpcClientProperties().get("timeout");
            try {
                long timeout = Long.parseLong(timeoutStr);
                if (timeout <= 0) {
                    errors.add(String.format("[%s] %s: Timeout must be positive (was %s ms)", getValidatorName(), prefix, timeoutStr));
                }
            } catch (NumberFormatException e) {
                errors.add(String.format("[%s] %s: Timeout must be a valid number (was '%s')", getValidatorName(), prefix, timeoutStr));
            }
        }
    }

    private void validateDuplicateOutputs(String prefix, PipelineStepConfig step, List<String> errors) {
        // Check for case-insensitive duplicates
        Set<String> lowerCaseNames = new HashSet<>();
        for (String outputName : step.outputs().keySet()) {
            if (!lowerCaseNames.add(outputName.toLowerCase())) {
                errors.add(String.format("[%s] %s: Duplicate output name '%s' (case-insensitive)", 
                          getValidatorName(), prefix, outputName));
            }
        }
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public String getValidatorName() {
        return "OutputRoutingValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Run in both PRODUCTION and DESIGN modes - output routing is important for design but can be relaxed for testing
        return Set.of(ValidationMode.PRODUCTION, ValidationMode.DESIGN);
    }
}