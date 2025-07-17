package io.pipeline.model.validation.validators.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.validation.PipelineConfigValidatable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validator for step names in pipeline configurations.
 * Validates that step names match the required pattern and suggests corrections when they don't.
 */
@ApplicationScoped
public class StepNameValidator implements FieldValidator<PipelineConfig> {

    private static final Pattern STEP_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$");
    
    private final ObjectMapper objectMapper;
    
    @Inject
    public StepNameValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public List<String> validate(PipelineConfig config, Set<ValidationMessage> errors, int currentDepth) {
        List<String> suggestions = new ArrayList<>();
        
        // Check for stepName issues in each step
        if (config.pipelineSteps() != null) {
            for (Map.Entry<String, PipelineStepConfig> entry : config.pipelineSteps().entrySet()) {
                String stepId = entry.getKey();
                PipelineStepConfig step = entry.getValue();
                
                // Check if stepName matches the key in the map
                if (!stepId.equals(step.stepName())) {
                    suggestions.add("- Step ID '" + stepId + "' should match stepName '" + step.stepName() + "'");
                }
                
                // Check for stepName format issues
                if (step.stepName() != null && !STEP_NAME_PATTERN.matcher(step.stepName()).matches()) {
                    String fixedStepName = StringCorrectionUtil.suggestCorrection(step.stepName(), STEP_NAME_PATTERN, "step");
                    suggestions.add("- Step name '" + step.stepName() + "' doesn't match required pattern. Suggested fix: '" + fixedStepName + "'");
                    
                    // Simulate validation with fixed stepName if we haven't reached max depth
                    if (currentDepth < 2) {
                        try {
                            // Create a modified step with the fixed name
                            PipelineStepConfig fixedStep = createFixedStep(step, fixedStepName);
                            
                            // Create a modified pipeline with the fixed step
                            PipelineConfig fixedConfig = createFixedPipeline(config, stepId, fixedStep);
                            
                            // Validate the fixed pipeline
                            JsonNode fixedNode = objectMapper.valueToTree(fixedConfig);
                            
                            // We don't have direct access to the schema here, so we'll just provide information
                            // about what would happen after fixing
                            suggestions.add("  - After fixing the step name, you would need to update any references to this step");
                            
                            // If this is a key step that others might depend on, add more specific guidance
                            if (isKeyStep(step)) {
                                suggestions.add("  - This appears to be a key step in your pipeline. Check for references in other steps' outputs");
                            }
                        } catch (Exception e) {
                            // Ignore errors in the suggestion process
                            suggestions.add("  - Error simulating fix: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        return suggestions;
    }
    
    @Override
    public boolean canHandle(PipelineConfigValidatable validatable) {
        return validatable instanceof PipelineConfig;
    }
    
    @Override
    public int getPriority() {
        return 90; // Lower than cluster validator
    }
    
    /**
     * Creates a fixed step with the corrected name.
     * 
     * @param original The original step
     * @param fixedName The fixed name
     * @return A new step with the fixed name
     */
    private PipelineStepConfig createFixedStep(PipelineStepConfig original, String fixedName) {
        // In a real implementation, you would create a copy with the fixed name
        // For this example, we'll just return the original step (the actual fix would be applied later)
        return original;
    }
    
    /**
     * Creates a fixed pipeline with the corrected step.
     * 
     * @param original The original pipeline
     * @param stepId The ID of the step to fix
     * @param fixedStep The fixed step
     * @return A new pipeline with the fixed step
     */
    private PipelineConfig createFixedPipeline(PipelineConfig original, String stepId, PipelineStepConfig fixedStep) {
        // In a real implementation, you would create a copy with the fixed step
        // For this example, we'll just return the original pipeline (the actual fix would be applied later)
        return original;
    }
    
    /**
     * Determines if a step is a key step that others might depend on.
     * 
     * @param step The step to check
     * @return true if this is a key step, false otherwise
     */
    private boolean isKeyStep(PipelineStepConfig step) {
        // In a real implementation, you would check if this step is referenced by other steps
        // For this example, we'll just check if it's a CONNECTOR or PIPELINE step
        return step.stepType() != null && 
               (step.stepType().name().equals("CONNECTOR") || step.stepType().name().equals("PIPELINE"));
    }
}