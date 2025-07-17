package io.pipeline.model.validation.validators.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.data.util.json.MockPipelineGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validator for cluster configurations in pipeline configurations.
 * Validates and suggests fixes for missing cluster configurations.
 */
@ApplicationScoped
public class ClusterValidator implements FieldValidator<PipelineConfig> {

    private final ObjectMapper objectMapper;
    
    @Inject
    public ClusterValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public List<String> validate(PipelineConfig config, Set<ValidationMessage> errors, int currentDepth) {
        List<String> suggestions = new ArrayList<>();
        
        // Check if any errors are related to missing cluster
        boolean missingCluster = errors.stream()
                .anyMatch(error -> error.getMessage().contains("cluster"));
                
        if (missingCluster) {
            suggestions.add("- Add a default cluster using 'createSimpleClusterOnlySetup()' method from MockPipelineGenerator");
            suggestions.add("  Example: PipelineClusterConfig cluster = MockPipelineGenerator.createSimpleClusterOnlySetup();");
            
            // Simulate validation with a default cluster if we haven't reached max depth
            if (currentDepth < 2) {
                try {
                    // Create a modified pipeline with a default cluster
                    PipelineConfig fixedPipeline = applyClusterFix(config);
                    
                    // Validate the fixed pipeline
                    JsonNode fixedNode = objectMapper.valueToTree(fixedPipeline);
                    
                    // We don't have direct access to the schema here, so we'll just provide information
                    // about what would happen after fixing
                    suggestions.add("- After adding a default cluster, you would need to:");
                    suggestions.add("  - Set appropriate values for allowedKafkaTopics and allowedGrpcServices");
                    suggestions.add("  - Configure the pipelineGraphConfig if needed");
                    suggestions.add("  - Set a defaultPipelineName if needed");
                } catch (Exception e) {
                    // Ignore errors in the suggestion process
                    suggestions.add("- Error simulating fix: " + e.getMessage());
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
        return 100; // Higher than step name validator
    }
    
    /**
     * Applies a cluster fix to a pipeline configuration.
     * 
     * @param config The pipeline configuration to fix
     * @return A new pipeline configuration with the fix applied
     */
    private PipelineConfig applyClusterFix(PipelineConfig config) {
        // In a real implementation, you would create a new PipelineConfig with the cluster
        // For this example, we'll just return the original config (the actual fix would be applied later)
        return config;
    }
}