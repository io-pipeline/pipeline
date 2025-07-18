package io.pipeline.model.validation;

import io.pipeline.api.validation.*;
import io.pipeline.common.validation.EmptyValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * A validator that combines multiple validators and runs them in priority order.
 * 
 * @param <T> The type of object being validated
 */
public class CompositeValidator<T extends ConfigValidatable> implements ConfigValidator<T> {

    private static final Logger LOG = Logger.getLogger(CompositeValidator.class);

    private final List<ConfigValidator<T>> validators;
    private final String name;

    /**
     * Default constructor for CDI.
     */
    public CompositeValidator() {
        this.name = "DefaultCompositeValidator";
        this.validators = new ArrayList<>();
    }

    public CompositeValidator(String name) {
        this.name = name;
        this.validators = new ArrayList<>();
    }

    public CompositeValidator(String name, List<ConfigValidator<T>> validators) {
        this.name = name;
        this.validators = new ArrayList<>(validators);
        // Sort by priority
        this.validators.sort(Comparator.comparingInt(ConfigValidator::getPriority));
    }

    /**
     * Adds a validator to this composite.
     * 
     * @param validator The validator to add
     * @return This composite for fluent API
     */
    public CompositeValidator<T> addValidator(ConfigValidator<T> validator) {
        validators.add(validator);
        // Re-sort by priority
        validators.sort(Comparator.comparingInt(ConfigValidator::getPriority));
        return this;
    }

    @Override
    public ValidationResult validate(T object) {
        // Default to PRODUCTION mode for backward compatibility
        return validate(object, ValidationMode.PRODUCTION);
    }

    /**
     * Validates an object using the specified validation mode.
     * Only runs validators that support the given mode.
     * 
     * @param object The object to validate
     * @param mode The validation mode to use
     * @return The combined validation result
     */
    public ValidationResult validate(T object, ValidationMode mode) {
        LOG.info("CompositeValidator.validate called with " + validators.size() + " validators in " + mode + " mode");
        ValidationResult result = EmptyValidationResult.instance();

        for (ConfigValidator<T> validator : validators) {
            String validatorName = validator.getValidatorName();
            LOG.info("Executing validator: " + validatorName);
            // Check if this validator supports the current mode
            if (!validator.supportedModes().contains(mode)) {
                LOG.info("Skipping validator " + validatorName + " - does not support " + mode + " mode");
                continue;
            }

            try {
                ValidationResult validatorResult;

                // Check if validator is mode-aware
                if (validator instanceof ModeAwareValidator) {
                    validatorResult = ((ModeAwareValidator<T>) validator).validate(object, mode);
                } else {
                    validatorResult = validator.validate(object);
                }

                // Add validator name prefix to each error and warning for better context
                List<String> prefixedErrors = new ArrayList<>();
                for (String error : validatorResult.errors()) {
                    prefixedErrors.add("[" + validatorName + "] " + error);
                }
                
                List<String> prefixedWarnings = new ArrayList<>();
                for (String warning : validatorResult.warnings()) {
                    prefixedWarnings.add("[" + validatorName + "] " + warning);
                }
                
                // Create a new validation result with prefixed messages
                ValidationResult prefixedResult;
                if (validatorResult.valid()) {
                    prefixedResult = ValidationResultFactory.successWithWarnings(prefixedWarnings);
                } else {
                    prefixedResult = ValidationResultFactory.failure(prefixedErrors, prefixedWarnings);
                }

                LOG.info("Validator " + validatorName + " returned: valid=" + validatorResult.valid() + 
                         ", errors=" + prefixedErrors + ", warnings=" + prefixedWarnings);
                result = result.combine(prefixedResult);
            } catch (Exception e) {
                // Log the error and add it to the validation result
                String errorMessage = String.format(
                    "[%s] Threw an exception: %s",
                    validatorName,
                    e.getMessage()
                );
                LOG.error("Validator exception", e);
                result = result.combine(ValidationResultFactory.failure(errorMessage));
            }
        }

        LOG.info("CompositeValidator final result: valid=" + result.valid() + ", errors=" + result.errors() + ", warnings=" + result.warnings());
        return result;
    }

    @Override
    public String getValidatorName() {
        return name;
    }

    @Override
    public int getPriority() {
        return 0; // Composite validators typically run first
    }

    /**
     * Returns the list of validators in this composite.
     * 
     * @return The list of validators
     */
    public List<ConfigValidator<T>> getValidators() {
        return validators;
    }
}
