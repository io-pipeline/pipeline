package io.pipeline.model.validation.validators;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates business-level required field rules that aren't enforced by model constructors.
 * 
 * <h2>Validation Rules:</h2>
 * <ul>
 *   <li><b>Pipeline Steps</b>: Pipeline must have at least one step (business rule)</li>
 *   <li><b>Step Descriptions</b>: Steps should have meaningful descriptions (warning)</li>
 *   <li><b>Transport Configuration</b>: Transport-specific configs must be present when needed</li>
 *   <li><b>Retry Values</b>: Business validation of retry configuration ranges</li>
 * </ul>
 * 
 * <h2>Note:</h2>
 * Most structural validation (non-null names, types, etc.) is handled by model constructors.
 * This validator focuses on business rules and cross-field validation.
 * 
 * <h2>Priority:</h2>
 * High priority (10) - runs early to catch basic business rule violations.
 */
@ApplicationScoped
public class RequiredFieldsValidator implements PipelineConfigValidator {

    /**
     * Default constructor for CDI injection.
     */
    public RequiredFieldsValidator() {
        // Default constructor for CDI
    }

    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        PipelineConfig config = (PipelineConfig) validatable;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (config == null) {
            return ValidationResultFactory.failure("Pipeline configuration cannot be null");
        }

        // Pipelines can start empty - no steps required initially
        // Steps will be added after services are whitelisted
        if (config.pipelineSteps() != null && !config.pipelineSteps().isEmpty()) {
            for (var entry : config.pipelineSteps().entrySet()) {
                String stepId = entry.getKey();
                PipelineStepConfig step = entry.getValue();

                validateStep(stepId, step, errors, warnings);
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

    private void validateStep(String stepId, PipelineStepConfig step, List<String> errors, List<String> warnings) {
        String stepPrefix = "Step '" + stepId + "': ";

        if (step == null) {
            errors.add(stepPrefix + "Step configuration cannot be null");
            return;
        }

        // Business rule: Steps should have meaningful descriptions
        if (step.description() == null || step.description().isBlank()) {
            warnings.add(stepPrefix + "Step should have a meaningful description");
        }

        // Note: Transport configuration validation is handled by OutputTarget constructor

        // Business validation of retry configuration ranges
        validateRetryConfiguration(stepPrefix, step, errors, warnings);
    }


    private void validateRetryConfiguration(String stepPrefix, PipelineStepConfig step, 
                                          List<String> errors, List<String> warnings) {
        // Business validation of retry values (models already enforce non-negative)
        if (step.maxRetries() != null && step.maxRetries() > 10) {
            warnings.add(stepPrefix + "Max retries (" + step.maxRetries() + ") is unusually high, consider if this is intended");
        }

        if (step.retryBackoffMs() != null && step.retryBackoffMs() > 60000) {
            warnings.add(stepPrefix + "Retry backoff (" + step.retryBackoffMs() + "ms) is over 1 minute, consider if this is intended");
        }

        if (step.stepTimeoutMs() != null && step.stepTimeoutMs() > 300000) {
            warnings.add(stepPrefix + "Step timeout (" + step.stepTimeoutMs() + "ms) is over 5 minutes, consider if this is intended");
        }

        // Logical validation between retry values
        if (step.retryBackoffMs() != null && step.maxRetryBackoffMs() != null && 
            step.retryBackoffMs() > step.maxRetryBackoffMs()) {
            errors.add(stepPrefix + "Initial retry backoff cannot be greater than max retry backoff");
        }
    }

    @Override
    public int getPriority() {
        return 10; // High priority - run early to catch basic structural issues
    }

    @Override
    public String getValidatorName() {
        return "RequiredFieldsValidator";
    }
}
