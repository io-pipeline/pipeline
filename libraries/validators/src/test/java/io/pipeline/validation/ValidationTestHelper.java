package io.pipeline.validation;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.InterPipelineLoopValidator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Helper class for validation tests that provides common test methods and utilities.
 * This reduces code duplication across the different validation mode test classes.
 */
public class ValidationTestHelper {

    /**
     * Tests that a cluster with a single empty pipeline is valid.
     * 
     * @param validator The validator to use
     * @param interPipelineLoopValidator The inter-pipeline loop validator
     * @param modeName The name of the validation mode being tested
     */
    public static void testEmptyClusterIsValid(
            PipelineConfigValidator validator,
            InterPipelineLoopValidator interPipelineLoopValidator,
            String modeName) {
        
        // 1. Generate the configuration object
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createClusterWithEmptyPipeline();

        // 2. Validate the cluster-level configuration
        ValidationResult clusterResult = interPipelineLoopValidator.validate(clusterConfig);
        assertTrue(clusterResult.valid(), "Cluster-level validation should pass in " + modeName + " mode.");
        
        // The placeholder validator currently returns a warning, so we check for that.
        assertFalse(clusterResult.warnings().isEmpty(), "Expected a warning from the placeholder InterPipelineLoopValidator.");
        assertEquals(1, clusterResult.warnings().size());
        assertEquals("Inter-pipeline loop detection is not yet implemented", clusterResult.warnings().get(0));

        // 3. Validate each pipeline within the cluster
        assertNotNull(clusterConfig.pipelineGraphConfig(), "PipelineGraphConfig should not be null.");
        assertNotNull(clusterConfig.pipelineGraphConfig().pipelines(), "Pipelines map should not be null.");

        for (PipelineConfig pipelineConfig : clusterConfig.pipelineGraphConfig().pipelines().values()) {
            ValidationResult pipelineResult = validator.validate(pipelineConfig);

            // An empty pipeline is valid and should have no errors or warnings.
            assertTrue(pipelineResult.valid(), 
                    "Pipeline '" + pipelineConfig.name() + "' should be valid in " + modeName + " mode.");
            assertTrue(pipelineResult.warnings().isEmpty(), 
                    "An empty pipeline should have no warnings in " + modeName + " mode.");
            assertTrue(pipelineResult.errors().isEmpty(), 
                    "Pipeline '" + pipelineConfig.name() + "' should have no errors in " + modeName + " mode.");
        }
    }

    /**
     * Tests validation of a pipeline with a naming convention violation (a dot in the name).
     * 
     * @param validator The validator to use
     * @param modeName The name of the validation mode being tested
     * @param shouldFail Whether validation should fail in this mode
     */
    public static void testPipelineWithNamingViolation(
            PipelineConfigValidator validator,
            String modeName,
            boolean shouldFail) {
        
        // 1. Generate the invalid configuration
        PipelineConfig invalidConfig = MockPipelineGenerator.createPipelineWithNamingViolation();

        // 2. Validate with the specified validator
        ValidationResult result = validator.validate(invalidConfig);

        // 3. Assert based on expected behavior for this mode
        if (shouldFail) {
            assertFalse(result.valid(), 
                    "Validation should fail for a pipeline with a dot in its name in " + modeName + " mode.");
            assertFalse(result.errors().isEmpty(), 
                    "Expected at least one validation error in " + modeName + " mode.");

            String expectedError = "Pipeline name 'invalid.pipeline.name' cannot contain dots - dots are reserved as delimiters in topic naming convention";
            assertTrue(result.errors().contains(expectedError), 
                    "The specific naming convention error should be reported in " + modeName + " mode.");
        } else {
            assertTrue(result.valid(), 
                    "Validation should pass for a pipeline with a dot in its name in " + modeName + " mode.");
        }
    }

    /**
     * Tests validation of a pipeline with incomplete processor information.
     * 
     * @param validator The validator to use
     * @param modeName The name of the validation mode being tested
     * @param shouldFail Whether validation should fail in this mode
     */
    public static void testPipelineWithIncompleteProcessorInfo(
            PipelineConfigValidator validator,
            String modeName,
            boolean shouldFail) {
        
        // 1. Generate the configuration with incomplete processor info
        PipelineConfig config = MockPipelineGenerator.createPipelineWithIncompleteProcessorInfo();

        // 2. Validate with the specified validator
        ValidationResult result = validator.validate(config);

        // 3. Assert based on expected behavior for this mode
        if (shouldFail) {
            assertFalse(result.valid(), 
                    "Validation should fail for a pipeline with incomplete processor info in " + modeName + " mode.");
        } else {
            assertTrue(result.valid(), 
                    "Validation should pass for a pipeline with incomplete processor info in " + modeName + " mode.");
        }
    }
}