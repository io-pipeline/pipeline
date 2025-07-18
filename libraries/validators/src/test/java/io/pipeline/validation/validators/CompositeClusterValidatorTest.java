package io.pipeline.validation.validators;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.ClusterIntraPipelineLoopValidator;
import io.pipeline.model.validation.validators.CompositeClusterValidator;
import io.pipeline.model.validation.validators.InterPipelineLoopValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the CompositeClusterValidator.
 * This validator combines the results of InterPipelineLoopValidator and ClusterIntraPipelineLoopValidator.
 */
@QuarkusTest
public class CompositeClusterValidatorTest {

    @Inject
    CompositeClusterValidator compositeClusterValidator;
    
    @Inject
    InterPipelineLoopValidator interPipelineLoopValidator;
    
    @Inject
    ClusterIntraPipelineLoopValidator clusterIntraPipelineLoopValidator;
    
    @Test
    void testEmptyClusterIsValid() {
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createClusterWithEmptyPipeline();
        
        // Validate using the composite validator
        ValidationResult compositeResult = compositeClusterValidator.validate(clusterConfig);
        assertTrue(compositeResult.valid(), "Composite cluster validation should pass for an empty cluster.");
        assertTrue(compositeResult.errors().isEmpty(), "Composite cluster validation should have no errors for an empty cluster.");
        
        // Validate using the individual validators for comparison
        ValidationResult interResult = interPipelineLoopValidator.validate(clusterConfig);
        assertTrue(interResult.valid(), "Inter-pipeline loop validation should pass for an empty cluster.");
        assertTrue(interResult.errors().isEmpty(), "Inter-pipeline loop validation should have no errors for an empty cluster.");
        
        ValidationResult intraResult = clusterIntraPipelineLoopValidator.validate(clusterConfig);
        assertTrue(intraResult.valid(), "Intra-pipeline loop validation should pass for an empty cluster.");
        assertTrue(intraResult.errors().isEmpty(), "Intra-pipeline loop validation should have no errors for an empty cluster.");
    }
    
    @Test
    void testPipelineWithUnregisteredService() {
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createPipelineWithUnregisteredService();
        
        // Validate using the composite validator
        ValidationResult compositeResult = compositeClusterValidator.validate(clusterConfig);
        assertTrue(compositeResult.valid(), "Composite cluster validation should pass for a pipeline with an unregistered service.");
        assertTrue(compositeResult.errors().isEmpty(), "Composite cluster validation should have no errors for a pipeline with an unregistered service.");
        
        // Validate using the individual validators for comparison
        ValidationResult interResult = interPipelineLoopValidator.validate(clusterConfig);
        assertTrue(interResult.valid(), "Inter-pipeline loop validation should pass for a pipeline with an unregistered service.");
        assertTrue(interResult.errors().isEmpty(), "Inter-pipeline loop validation should have no errors for a pipeline with an unregistered service.");
        
        ValidationResult intraResult = clusterIntraPipelineLoopValidator.validate(clusterConfig);
        assertTrue(intraResult.valid(), "Intra-pipeline loop validation should pass for a pipeline with an unregistered service.");
        assertTrue(intraResult.errors().isEmpty(), "Intra-pipeline loop validation should have no errors for a pipeline with an unregistered service.");
    }
}