package io.pipeline.model.validation;

import io.pipeline.api.validation.ConfigValidatable;
import io.pipeline.api.validation.ConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.EmptyValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder for creating CompositeValidator instances.
 * This provides a fluent API for constructing validators with specific configurations.
 * 
 * @param <T> The type of object being validated
 */
public class CompositeValidatorBuilder<T extends ConfigValidatable> {

    private String name = "CompositeValidator";
    private final List<ConfigValidator<T>> validators = new ArrayList<>();

    /**
     * Default constructor. Use the static create() method to instantiate.
     */
    private CompositeValidatorBuilder() {
        // Private constructor to enforce use of factory method
    }

    /**
     * Creates a new builder instance.
     * 
     * @param <T> The type of object being validated
     * @return A new CompositeValidatorBuilder instance
     */
    public static <T extends ConfigValidatable> CompositeValidatorBuilder<T> create() {
        return new CompositeValidatorBuilder<>();
    }

    /**
     * Sets the name of the composite validator.
     * 
     * @param name The name to set for the validator
     * @return This builder instance for method chaining
     */
    public CompositeValidatorBuilder<T> withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Adds a validator to the composite.
     * 
     * @param validator The validator to add
     * @return This builder instance for method chaining
     */
    public CompositeValidatorBuilder<T> addValidator(ConfigValidator<T> validator) {
        this.validators.add(validator);
        return this;
    }

    /**
     * Adds multiple validators to the composite.
     * 
     * @param validators The validators to add
     * @return This builder instance for method chaining
     */
    @SafeVarargs
    public final CompositeValidatorBuilder<T> addValidators(ConfigValidator<T>... validators) {
        this.validators.addAll(Arrays.asList(validators));
        return this;
    }

    /**
     * Adds a list of validators to the composite.
     * 
     * @param validators The list of validators to add
     * @return This builder instance for method chaining
     */
    public CompositeValidatorBuilder<T> addValidators(List<ConfigValidator<T>> validators) {
        this.validators.addAll(validators);
        return this;
    }

    /**
     * Creates an empty validator that always returns success.
     * Useful for testing when you don't want validation to interfere.
     * 
     * @return This builder instance for method chaining
     */
    public CompositeValidatorBuilder<T> withEmptyValidation() {
        this.validators.clear();
        this.validators.add(new ConfigValidator<T>() {
            @Override
            public ValidationResult validate(T config) {
                return EmptyValidationResult.instance();
            }

            @Override
            public String getValidatorName() {
                return "EmptyValidator";
            }
        });
        return this;
    }

    /**
     * Creates a validator that always fails with the given error.
     * Useful for testing error handling.
     * 
     * @param errorMessage The error message to return when validating
     * @return This builder instance for method chaining
     */
    public CompositeValidatorBuilder<T> withFailingValidation(String errorMessage) {
        this.validators.clear();
        this.validators.add(new ConfigValidator<T>() {
            @Override
            public ValidationResult validate(T config) {
                return ValidationResultFactory.failure(errorMessage);
            }

            @Override
            public String getValidatorName() {
                return "FailingValidator";
            }
        });
        return this;
    }

    /**
     * Builds the CompositeValidator with the configured validators.
     * 
     * @return A new CompositeValidator instance with the configured settings
     */
    public CompositeValidator<T> build() {
        return new CompositeValidator<>(name, validators);
    }
}
