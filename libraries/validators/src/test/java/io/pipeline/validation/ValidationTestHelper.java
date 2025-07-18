
package io.pipeline.validation;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.InterPipelineLoopValidator;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationTestHelper {

    public static void testEmptyClusterIsValid(PipelineConfigValidator validator, InterPipelineLoopValidator clusterValidator, String mode) {
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createClusterWithEmptyPipeline();

        ValidationResult clusterResult = clusterValidator.validate(clusterConfig);
        assertTrue(clusterResult.valid(), "Cluster-level validation should pass in " + mode + " mode.");

        for (PipelineConfig pipelineConfig : clusterConfig.pipelineGraphConfig().pipelines().values()) {
            ValidationResult pipelineResult = validator.validate(pipelineConfig);
            assertTrue(pipelineResult.valid(), "Pipeline '" + pipelineConfig.name() + "' should be valid in " + mode + " mode.");
            assertTrue(pipelineResult.errors().isEmpty(), "Pipeline '" + pipelineConfig.name() + "' should have no errors in " + mode + " mode.");
        }
    }

    public static void testPipelineWithNamingViolation(PipelineConfigValidator validator, String mode, boolean shouldFail) {
        PipelineConfig invalidConfig = MockPipelineGenerator.createPipelineWithNamingViolation();

        ValidationResult result = validator.validate(invalidConfig);

        if (shouldFail) {
            assertFalse(result.valid(), "Validation should fail for a pipeline with a dot in its name in " + mode + " mode.");
            assertFalse(result.errors().isEmpty(), "Expected at least one validation error in " + mode + " mode.");
            String expectedError = "[NamingConventionValidator] Pipeline name 'invalid.pipeline.name' cannot contain dots - dots are reserved as delimiters in topic naming convention";
            assertTrue(result.errors().contains(expectedError), "The specific naming convention error with validator name should be reported in " + mode + " mode.");
        } else {
            assertTrue(result.valid(), "Validation should pass for a pipeline with a dot in its name in " + mode + " mode.");
        }
    }
    
    public static void testPipelineWithIncompleteProcessorInfo(PipelineConfigValidator validator, String mode, boolean shouldFail) {
        PipelineConfig invalidConfig = MockPipelineGenerator.createPipelineWithIncompleteProcessorInfo();

        ValidationResult result = validator.validate(invalidConfig);

        if (shouldFail) {
            assertFalse(result.valid(), "Validation should fail for a pipeline with incomplete processor info in " + mode + " mode.");
            assertFalse(result.errors().isEmpty(), "Expected at least one validation error in " + mode + " mode.");
            String expectedError = "[ProcessorInfoValidator] Step 'processor-step': gRPC service name 'ab' is too short (minimum 3 characters)";
            assertTrue(result.errors().stream().anyMatch(e -> e.contains(expectedError)), "The specific processor info error with validator name should be reported in " + mode + " mode.");
        } else {
            assertTrue(result.valid(), "Validation should pass for a pipeline with incomplete processor info in " + mode + " mode.");
        }
    }

    public static void testPipelineWithSingleStepNoRouting(PipelineConfigValidator validator, String mode) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithSingleStepNoRouting();

        ValidationResult result = validator.validate(config);

        assertTrue(result.valid(), "Validation should pass for a pipeline with a single step and no routing in " + mode + " mode.");
        assertFalse(result.warnings().isEmpty(), "Expected warnings for a pipeline with a single step and no routing in " + mode + " mode.");
        String expectedWarning1 = "[StepTypeValidator] Step 'echo-step': PIPELINE steps typically have inputs";
        String expectedWarning2 = "[StepTypeValidator] Step 'echo-step': PIPELINE steps typically have outputs";
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains(expectedWarning1)), "The specific input warning with validator name should be reported in " + mode + " mode.");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains(expectedWarning2)), "The specific output warning with validator name should be reported in " + mode + " mode.");
    }

    public static void testPipelineWithConnectorStep(PipelineConfigValidator validator, String mode) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithConnectorStep();

        ValidationResult result = validator.validate(config);

        assertTrue(result.valid(), "Validation should pass for a pipeline with a single connector step in " + mode + " mode.");
        assertTrue(result.warnings().isEmpty(), "Expected no warnings for a complete pipeline in " + mode + " mode.");
    }
}
