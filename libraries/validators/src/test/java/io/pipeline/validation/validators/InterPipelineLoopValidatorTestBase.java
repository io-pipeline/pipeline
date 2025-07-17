package io.pipeline.validation.validators;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineGraphConfig;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.validators.InterPipelineLoopValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public abstract class InterPipelineLoopValidatorTestBase {
    
    protected abstract InterPipelineLoopValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        InterPipelineLoopValidator validator = getValidator();
        assertEquals(100, validator.getPriority());
        assertEquals("InterPipelineLoopValidator", validator.getValidatorName());
    }
    
    @Test
    void testNullClusterConfiguration() {
        ValidationResult result = getValidator().validate(null);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testNullPipelineGraphConfig() {
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            null,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
    
    @Test
    void testEmptyPipelines() {
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Collections.emptyMap()
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Inter-pipeline loop detection is not yet implemented"));
    }
    
    @Test
    void testSinglePipeline() {
        PipelineConfig pipeline = new PipelineConfig(
            "test-pipeline",
            Collections.emptyMap()
        );
        
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of("pipeline1", pipeline)
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Inter-pipeline loop detection is not yet implemented"));
    }
    
    @Test
    void testMultiplePipelines() {
        PipelineConfig pipeline1 = new PipelineConfig(
            "pipeline1",
            Collections.emptyMap()
        );
        
        PipelineConfig pipeline2 = new PipelineConfig(
            "pipeline2",
            Collections.emptyMap()
        );
        
        PipelineConfig pipeline3 = new PipelineConfig(
            "pipeline3",
            Collections.emptyMap()
        );
        
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of(
                "pipeline1", pipeline1,
                "pipeline2", pipeline2,
                "pipeline3", pipeline3
            )
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null,
            null,
            Collections.emptySet(),
            Collections.emptySet()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Inter-pipeline loop detection is not yet implemented"));
    }
    
    // TODO: Add more tests when loop detection is implemented
    // For now, these tests just verify the validator doesn't crash
    // and returns the expected warning about incomplete implementation
}