package io.pipeline.model.validation.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.field.FieldValidator;
import io.pipeline.model.validation.validators.field.FieldValidatorRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SchemaComplianceValidator implements PipelineConfigValidator {

    private final JsonSchema schema;
    private final ObjectMapper objectMapper;
    private final int maxRecursionDepth;
    private final FieldValidatorRegistry validatorRegistry;

    @Inject
    public SchemaComplianceValidator(
            ObjectMapper objectMapper,
            FieldValidatorRegistry validatorRegistry,
            @ConfigProperty(name = "pipeline.validation.max-recursion-depth", defaultValue = "2") int maxRecursionDepth) {
        this.objectMapper = objectMapper;
        this.validatorRegistry = validatorRegistry;
        this.maxRecursionDepth = maxRecursionDepth;
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        InputStream schemaStream = getClass().getResourceAsStream("/pipeline-schema.json");
        this.schema = factory.getSchema(schemaStream);
    }
    
    // Constructor for testing with custom recursion depth
    public SchemaComplianceValidator(ObjectMapper objectMapper, int maxRecursionDepth, JsonSchema schema, FieldValidatorRegistry validatorRegistry) {
        this.objectMapper = objectMapper;
        this.maxRecursionDepth = maxRecursionDepth;
        this.schema = schema;
        this.validatorRegistry = validatorRegistry;
    }

    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        try {
            JsonNode jsonNode = objectMapper.valueToTree(validatable);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            
            if (errors.isEmpty()) {
                return ValidationResultFactory.success();
            } else {
                List<String> errorMessages = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.toList());
                
                // Check if we can suggest fixes
                List<String> suggestedFixes = suggestFixes(validatable, errors, 0);
                if (!suggestedFixes.isEmpty()) {
                    errorMessages.add("\nSuggested fixes:");
                    errorMessages.addAll(suggestedFixes);
                }
                
                return ValidationResultFactory.failure(errorMessages);
            }
        } catch (Exception e) {
            return ValidationResultFactory.failure("Failed to validate against JSON schema: " + e.getMessage());
        }
    }
    
    /**
     * Suggests fixes for validation errors with configurable recursion depth.
     * 
     * @param validatable The object being validated
     * @param errors The validation errors
     * @param currentDepth The current recursion depth
     * @return A list of suggested fixes
     */
    private List<String> suggestFixes(PipelineConfigValidatable validatable, 
                                     Set<ValidationMessage> errors, 
                                     int currentDepth) {
        // Stop recursion if we've reached the maximum depth
        if (currentDepth >= maxRecursionDepth) {
            return Collections.singletonList("Maximum recursion depth reached. Some additional issues may not be shown.");
        }
        
        List<String> suggestions = new ArrayList<>();
        
        // Get all applicable field validators for this validatable object
        List<FieldValidator<?>> validators = validatorRegistry.getValidatorsFor(validatable);
        
        // Apply each validator and collect suggestions
        for (FieldValidator<?> validator : validators) {
            try {
                // Use type erasure to our advantage here - we know the validator can handle this type
                @SuppressWarnings("unchecked")
                FieldValidator<PipelineConfigValidatable> typedValidator = (FieldValidator<PipelineConfigValidatable>) validator;
                
                // Apply the validator and collect suggestions
                List<String> validatorSuggestions = typedValidator.validate(validatable, errors, currentDepth);
                if (!validatorSuggestions.isEmpty()) {
                    suggestions.addAll(validatorSuggestions);
                }
            } catch (Exception e) {
                // Ignore errors in the validation process
                suggestions.add("- Error applying validator " + validator.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        
        // If no field validators provided suggestions, fall back to the legacy approach
        if (suggestions.isEmpty() && validatable instanceof PipelineConfig pipelineConfig) {
            // Legacy cluster validation logic
            boolean missingCluster = errors.stream()
                    .anyMatch(error -> error.getMessage().contains("cluster"));
                    
            if (missingCluster) {
                suggestions.add("- Add a default cluster using 'createSimpleClusterOnlySetup()' method");
                
                // Simulate validation with a default cluster to show further errors
                try {
                    // Create a modified pipeline with a default cluster
                    PipelineConfig fixedPipeline = applyClusterFix(pipelineConfig);
                    
                    // Validate the fixed pipeline and recurse to next level
                    JsonNode fixedNode = objectMapper.valueToTree(fixedPipeline);
                    Set<ValidationMessage> remainingErrors = schema.validate(fixedNode);
                    
                    if (!remainingErrors.isEmpty() && !remainingErrors.equals(errors)) {
                        suggestions.add("- With default cluster added, you would still have these issues:");
                        
                        // Add direct error messages
                        remainingErrors.stream()
                                .filter(error -> !errors.contains(error))
                                .map(ValidationMessage::getMessage)
                                .forEach(msg -> suggestions.add("  - " + msg));
                        
                        // Recurse to find deeper issues with incremented depth
                        if (currentDepth < maxRecursionDepth - 1) {
                            List<String> deeperSuggestions = suggestFixes(fixedPipeline, remainingErrors, currentDepth + 1);
                            if (!deeperSuggestions.isEmpty()) {
                                suggestions.add("- After fixing the cluster issue, additional fixes may be needed:");
                                suggestions.addAll(deeperSuggestions.stream()
                                        .map(s -> "  " + s)
                                        .collect(Collectors.toList()));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore errors in the suggestion process
                    suggestions.add("- Error simulating fix: " + e.getMessage());
                }
            }
        }
        
        return suggestions;
    }
    
    /**
     * Applies a cluster fix to a pipeline configuration.
     * 
     * @param config The pipeline configuration to fix
     * @return A new pipeline configuration with the fix applied
     */
    private PipelineConfig applyClusterFix(PipelineConfig config) {
        // This is a simplified approach - in a real implementation, you would
        // create a new PipelineConfig with the cluster
        // For demonstration purposes, we're just returning the original config
        // In a real implementation, you would create a copy with the cluster added
        return config;
    }

    @Override
    public int getPriority() {
        return 10; // Highest priority
    }

    @Override
    public String getValidatorName() {
        return "SchemaComplianceValidator";
    }
}
