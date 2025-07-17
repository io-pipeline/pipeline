package io.pipeline.model.validation.validators.field;

import com.networknt.schema.ValidationMessage;
import io.pipeline.api.validation.PipelineConfigValidatable;

import java.util.List;
import java.util.Set;

/**
 * Interface for field validators that validate specific fields in pipeline configurations.
 * Field validators are used by the SchemaComplianceValidator to provide field-specific
 * validation and correction suggestions.
 *
 * @param <T> The type of configuration object being validated, must implement PipelineConfigValidatable
 */
public interface FieldValidator<T extends PipelineConfigValidatable> {
    
    /**
     * Validates the given configuration object and returns a list of suggested fixes.
     * 
     * @param validatable The configuration object to validate
     * @param errors The validation errors from schema validation
     * @param currentDepth The current recursion depth
     * @return A list of suggested fixes
     */
    List<String> validate(T validatable, Set<ValidationMessage> errors, int currentDepth);
    
    /**
     * Determines if this validator can handle the given configuration object.
     * 
     * @param validatable The configuration object to check
     * @return true if this validator can handle the given object, false otherwise
     */
    boolean canHandle(PipelineConfigValidatable validatable);
    
    /**
     * Returns the priority of this validator (higher numbers = higher priority).
     * Validators with higher priority are executed first.
     * 
     * @return Priority level (default is 0)
     */
    default int getPriority() {
        return 0;
    }
}