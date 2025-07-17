package io.pipeline.model.validation.validators.field;

import io.pipeline.api.validation.PipelineConfigValidatable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Registry for field validators.
 * Collects and manages all field validators, sorting them by priority.
 */
@ApplicationScoped
public class FieldValidatorRegistry {

    private final List<FieldValidator<?>> validators;

    /**
     * Creates a new FieldValidatorRegistry with the given validators.
     * Validators are sorted by priority (higher priority first).
     *
     * @param validators The validators to register
     */
    @Inject
    public FieldValidatorRegistry(Instance<FieldValidator<?>> validators) {
        this.validators = StreamSupport.stream(validators.spliterator(), false)
                .sorted(Comparator.<FieldValidator<?>>comparingInt(FieldValidator::getPriority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Constructor for testing with explicit validators.
     *
     * @param validators The validators to register
     */
    public FieldValidatorRegistry(List<FieldValidator<?>> validators) {
        this.validators = validators.stream()
                .sorted(Comparator.<FieldValidator<?>>comparingInt(FieldValidator::getPriority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Returns all validators that can handle the given validatable object.
     *
     * @param validatable The validatable object
     * @return A list of validators that can handle the given object, sorted by priority
     */
    public List<FieldValidator<?>> getValidatorsFor(PipelineConfigValidatable validatable) {
        return validators.stream()
                .filter(validator -> validator.canHandle(validatable))
                .collect(Collectors.toList());
    }

    /**
     * Returns all registered validators.
     *
     * @return A list of all registered validators, sorted by priority
     */
    public List<FieldValidator<?>> getAllValidators() {
        return validators;
    }
}