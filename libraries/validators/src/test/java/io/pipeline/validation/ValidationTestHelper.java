package io.pipeline.validation;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.CompositeClusterValidator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidationTestHelper {

    public static void testEmptyClusterIsValid(PipelineConfigValidator validator, CompositeClusterValidator clusterValidator, String mode) {
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createClusterWithEmptyPipeline();

        ValidationResult clusterResult = clusterValidator.validate(clusterConfig);
        assertThat("Cluster-level validation should pass in " + mode + " mode.", clusterResult.valid(), is(true));

        for (PipelineConfig pipelineConfig : clusterConfig.pipelineGraphConfig().pipelines().values()) {
            ValidationResult pipelineResult = validator.validate(pipelineConfig);
            assertThat("Pipeline '" + pipelineConfig.name() + "' should be valid in " + mode + " mode.", 
                    pipelineResult.valid(), is(true));
            assertThat("Pipeline '" + pipelineConfig.name() + "' should have no errors in " + mode + " mode.", 
                    pipelineResult.errors(), is(empty()));
        }
    }

    public static void testPipelineWithNamingViolation(PipelineConfigValidator validator, String mode, boolean shouldFail) {
        PipelineConfig invalidConfig = MockPipelineGenerator.createPipelineWithNamingViolation();

        ValidationResult result = validator.validate(invalidConfig);

        if (shouldFail) {
            assertThat("Validation should fail for a pipeline with a dot in its name in " + mode + " mode.", 
                    result.valid(), is(false));
            assertThat("Expected at least one validation error in " + mode + " mode.", 
                    result.errors(), not(empty()));
            String expectedError = "[NamingConventionValidator] Pipeline name 'invalid.pipeline.name' cannot contain dots - dots are reserved as delimiters in topic naming convention";
            assertThat("The specific naming convention error with validator name should be reported in " + mode + " mode.", 
                    result.errors(), hasItem(expectedError));
            
            // Print the actual errors for debugging if the test fails
            if (!result.errors().contains(expectedError)) {
                System.out.println("[DEBUG_LOG] Expected error not found: " + expectedError);
                for (String error : result.errors()) {
                    System.out.println("[DEBUG_LOG] Actual error: " + error);
                }
            }
        } else {
            assertThat("Validation should pass for a pipeline with a dot in its name in " + mode + " mode.", 
                    result.valid(), is(true));
        }
    }
    
    public static void testPipelineWithIncompleteProcessorInfo(PipelineConfigValidator validator, String mode, boolean shouldFail) {
        PipelineConfig invalidConfig = MockPipelineGenerator.createPipelineWithIncompleteProcessorInfo();

        ValidationResult result = validator.validate(invalidConfig);

        if (shouldFail) {
            assertThat("Validation should fail for a pipeline with incomplete processor info in " + mode + " mode.", 
                    result.valid(), is(false));
            assertThat("Expected at least one validation error in " + mode + " mode.", 
                    result.errors(), not(empty()));
            String expectedError = "[ProcessorInfoValidator] Step 'processor-step': gRPC service name 'ab' is too short (minimum 3 characters)";
            assertThat("The specific processor info error with validator name should be reported in " + mode + " mode.", 
                    result.errors(), hasItem(containsString(expectedError)));
            
            // Print the actual errors for debugging if the test fails
            boolean foundExpectedError = result.errors().stream().anyMatch(e -> e.contains(expectedError));
            if (!foundExpectedError) {
                System.out.println("[DEBUG_LOG] Expected error not found: " + expectedError);
                for (String error : result.errors()) {
                    System.out.println("[DEBUG_LOG] Actual error: " + error);
                }
            }
        } else {
            assertThat("Validation should pass for a pipeline with incomplete processor info in " + mode + " mode.", 
                    result.valid(), is(true));
        }
    }

    public static void testPipelineWithSingleStepNoRouting(PipelineConfigValidator validator, String mode) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithSingleStepNoRouting();

        ValidationResult result = validator.validate(config);

        assertThat("Validation should pass for a pipeline with a single step and no routing in " + mode + " mode.", 
                result.valid(), is(true));
        assertThat("Expected warnings for a pipeline with a single step and no routing in " + mode + " mode.", 
                result.warnings(), not(empty()));
        String expectedWarning1 = "[StepTypeValidator] Step 'echo-step': PIPELINE steps typically have inputs";
        String expectedWarning2 = "[StepTypeValidator] Step 'echo-step': PIPELINE steps typically have outputs";
        assertThat("The specific input warning with validator name should be reported in " + mode + " mode.", 
                result.warnings(), hasItem(containsString(expectedWarning1)));
        assertThat("The specific output warning with validator name should be reported in " + mode + " mode.", 
                result.warnings(), hasItem(containsString(expectedWarning2)));
        
        // Print the actual warnings for debugging if the test fails
        boolean foundWarning1 = result.warnings().stream().anyMatch(w -> w.contains(expectedWarning1));
        boolean foundWarning2 = result.warnings().stream().anyMatch(w -> w.contains(expectedWarning2));
        if (!foundWarning1 || !foundWarning2) {
            System.out.println("[DEBUG_LOG] Expected warnings not found:");
            if (!foundWarning1) System.out.println("[DEBUG_LOG] Missing: " + expectedWarning1);
            if (!foundWarning2) System.out.println("[DEBUG_LOG] Missing: " + expectedWarning2);
            for (String warning : result.warnings()) {
                System.out.println("[DEBUG_LOG] Actual warning: " + warning);
            }
        }
    }

    public static void testPipelineWithConnectorStep(PipelineConfigValidator validator, String mode) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithConnectorStep();

        ValidationResult result = validator.validate(config);

        assertThat("Validation should pass for a pipeline with a single connector step in " + mode + " mode.", 
                result.valid(), is(true));
        assertThat("Expected no warnings for a complete pipeline in " + mode + " mode.", 
                result.warnings(), is(empty()));
        
        // Print the actual warnings for debugging if the test fails
        if (!result.warnings().isEmpty()) {
            System.out.println("[DEBUG_LOG] Unexpected warnings found:");
            for (String warning : result.warnings()) {
                System.out.println("[DEBUG_LOG] Warning: " + warning);
            }
        }
    }

    public static void testPipelineWithInvalidTopicName(PipelineConfigValidator validator, String mode, boolean shouldFail) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithInvalidTopicName();

        ValidationResult result = validator.validate(config);

        if (shouldFail) {
            assertThat("Validation should fail for a pipeline with an invalid topic name in " + mode + " mode.", 
                    result.valid(), is(false));
            assertThat("Expected at least one validation error in " + mode + " mode.", 
                    result.errors(), not(empty()));
            String expectedError = "[NamingConventionValidator] Topic 'my-custom-topic' doesn't follow the required naming pattern '{pipeline-name}.{step-name}.input'";
            assertThat("The specific topic naming error should be reported in " + mode + " mode.", 
                    result.errors(), hasItem(containsString(expectedError)));
            
            // Print the actual errors for debugging if the test fails
            boolean foundExpectedError = result.errors().stream().anyMatch(e -> e.contains(expectedError));
            if (!foundExpectedError) {
                System.out.println("[DEBUG_LOG] Expected error not found: " + expectedError);
                for (String error : result.errors()) {
                    System.out.println("[DEBUG_LOG] Actual error: " + error);
                }
            }
        } else {
            assertThat("Validation should pass for a pipeline with an invalid topic name in " + mode + " mode.", 
                    result.valid(), is(true));
        }
    }

    public static void testPipelineWithInvalidConsumerGroup(PipelineConfigValidator validator, String mode, boolean shouldFail) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithInvalidConsumerGroup();

        ValidationResult result = validator.validate(config);

        if (shouldFail) {
            assertThat("Validation should fail for a pipeline with an invalid consumer group in " + mode + " mode.", 
                    result.valid(), is(false));
            assertThat("Expected at least one validation error in " + mode + " mode.", 
                    result.errors(), not(empty()));
            String expectedError = "[NamingConventionValidator] Consumer group 'my-custom-consumer-group' doesn't follow the required naming pattern '{pipeline-name}.consumer-group'";
            assertThat("The specific consumer group naming error should be reported in " + mode + " mode.", 
                    result.errors(), hasItem(containsString(expectedError)));
            
            // Print the actual errors for debugging if the test fails
            boolean foundExpectedError = result.errors().stream().anyMatch(e -> e.contains(expectedError));
            if (!foundExpectedError) {
                System.out.println("[DEBUG_LOG] Expected error not found: " + expectedError);
                for (String error : result.errors()) {
                    System.out.println("[DEBUG_LOG] Actual error: " + error);
                }
            }
        } else {
            assertThat("Validation should pass for a pipeline with an invalid consumer group in " + mode + " mode.", 
                    result.valid(), is(true));
        }
    }

    public static void testPipelineWithMismatchedProcessor(PipelineConfigValidator validator, String mode, boolean shouldFail) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithMismatchedProcessor();

        ValidationResult result = validator.validate(config);

        if (shouldFail) {
            assertThat("Validation should fail for a pipeline with a mismatched processor in " + mode + " mode.", result.valid(), is(false));
            String expectedError = "[ProcessorInfoValidator] Step 'gutenberg-pg-connector': CONNECTOR steps should use an external gRPC service (grpcServiceName), not an internal bean.";
            assertThat("The specific mismatched processor error should be reported in " + mode + " mode.", result.errors(), hasItem(containsString(expectedError)));
        } else {
            assertThat("Validation should pass for a pipeline with a mismatched processor in " + mode + " mode.", result.valid(), is(true));
        }
    }

    public static void testPipelineWithDisabledRetries(PipelineConfigValidator validator, String mode) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithDisabledRetries();

        ValidationResult result = validator.validate(config);

        assertThat("Validation should pass for a pipeline with disabled retries in " + mode + " mode.", result.valid(), is(true));
        assertThat("Expected no warnings for a pipeline with disabled retries in " + mode + " mode.", result.warnings(), is(empty()));
    }

    public static void testPipelineWithUnregisteredService(PipelineConfigValidator validator, CompositeClusterValidator clusterValidator, String mode, boolean shouldFail) {
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createPipelineWithUnregisteredService();

        ValidationResult clusterResult = clusterValidator.validate(clusterConfig);
        // This is a placeholder for now, as the cluster validator is not fully implemented
        assertThat("Cluster-level validation should pass in " + mode + " mode.", clusterResult.valid(), is(true));

        for (PipelineConfig pipelineConfig : clusterConfig.pipelineGraphConfig().pipelines().values()) {
            ValidationResult pipelineResult = validator.validate(pipelineConfig);
            if (shouldFail) {
                assertThat("Validation should fail for a pipeline with an unregistered service in " + mode + " mode.", pipelineResult.valid(), is(false));
                String expectedError = "[StepReferenceValidator] Step 'echo-connector' references gRPC service 'unregistered-service' which is not registered in the cluster's allowed services.";
                assertThat("The specific unregistered service error should be reported in " + mode + " mode.", pipelineResult.errors(), hasItem(containsString(expectedError)));
            } else {
                assertThat("Validation should pass for a pipeline with an unregistered service in " + mode + " mode.", pipelineResult.valid(), is(true));
            }
        }
    }
    
    /**
     * Tests validation of a pipeline with a direct two-step loop (A -> B -> A).
     * This tests the IntraPipelineLoopValidator's ability to detect simple loops.
     * 
     * @param validator The validator to use
     * @param clusterValidator The cluster validator to use (not used in this test)
     * @param mode The validation mode being tested ("PRODUCTION", "DESIGN", or "TESTING")
     * @param shouldFail Whether validation should fail in this mode
     */
    public static void testPipelineWithDirectTwoStepLoop(PipelineConfigValidator validator, CompositeClusterValidator clusterValidator, String mode, boolean shouldFail) {
        PipelineConfig config = MockPipelineGenerator.createPipelineWithDirectTwoStepLoop();
        
        // Skip cluster validation and directly validate the pipeline
        ValidationResult result = validator.validate(config);
        
        if (shouldFail) {
            assertThat("Validation should fail for a pipeline with a direct two-step loop in " + mode + " mode.", 
                    result.valid(), is(false));
            
            String expectedError = "Detected a loop in pipeline 'pipeline-with-direct-loop'";
            assertThat("The specific loop detection error should be reported in " + mode + " mode.", 
                    result.errors(), hasItem(containsString(expectedError)));
            
            // The error should mention the steps involved in the loop
            assertThat("The error should mention the steps involved in the loop.", 
                    result.errors(), hasItem(allOf(
                            containsString("step-a"),
                            containsString("step-b")
                    )));
            
            // Print the actual errors for debugging
            if (!result.errors().isEmpty()) {
                for (String error : result.errors()) {
                    System.out.println("[DEBUG_LOG] Actual error: " + error);
                }
            }
        } else {
            assertThat("Validation should pass for a pipeline with a direct two-step loop in " + mode + " mode.", 
                    result.valid(), is(true));
        }
    }
    
    /**
     * Tests validation of a cluster with a direct inter-pipeline loop (Pipeline A -> Pipeline B -> Pipeline A).
     * This tests the InterPipelineLoopValidator's ability to detect loops across pipelines.
     * 
     * @param clusterValidator The cluster validator to use
     * @param mode The validation mode being tested ("PRODUCTION", "DESIGN", or "TESTING")
     * @param shouldFail Whether validation should fail in this mode
     */
    public static void testClusterWithDirectInterPipelineLoop(CompositeClusterValidator clusterValidator, String mode, boolean shouldFail) {
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createClusterWithDirectInterPipelineLoop();
        
        // Validate the cluster
        ValidationResult result = clusterValidator.validate(clusterConfig);
        
        if (shouldFail) {
            assertThat("Validation should fail for a cluster with a direct inter-pipeline loop in " + mode + " mode.", 
                    result.valid(), is(false));
            
            String expectedError = "Detected a loop across pipelines in cluster 'test-cluster'";
            assertThat("The specific loop detection error should be reported in " + mode + " mode.", 
                    result.errors(), hasItem(containsString(expectedError)));
            
            // The error should mention both pipelines involved in the loop
            assertThat("The error should mention both pipelines involved in the loop.", 
                    result.errors(), hasItem(allOf(
                            containsString("pipeline-a:step-a"),
                            containsString("pipeline-b:step-b")
                    )));
            
            // Print the actual errors for debugging
            if (!result.errors().isEmpty()) {
                for (String error : result.errors()) {
                    System.out.println("[DEBUG_LOG] Actual error: " + error);
                }
            }
        } else {
            assertThat("Validation should pass for a cluster with a direct inter-pipeline loop in " + mode + " mode.", 
                    result.valid(), is(true));
        }
    }
}