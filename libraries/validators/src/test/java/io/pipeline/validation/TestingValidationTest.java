package io.pipeline.validation;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.Composite;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.CompositeClusterValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the TESTING validation mode.
 * In TESTING mode:
 * - Only essential validators run
 * - Many warnings are ignored
 * - Minimal structural validation is enforced
 * - Naming conventions are relaxed
 * - Processor information is optional
 * - Loop detection is disabled
 */
@QuarkusTest
public class TestingValidationTest {

    @Inject
    @Composite
    @Named("testingPipelineValidator")
    PipelineConfigValidator testingPipelineValidator;

    @Inject
    CompositeClusterValidator compositeClusterValidator;

    @Test
    void testEmptyClusterIsValidInTesting() {
        ValidationTestHelper.testEmptyClusterIsValid(
                testingPipelineValidator,
                compositeClusterValidator,
                "TESTING"
        );
    }

    @Test
    void testPipelineWithNamingViolationPassesInTesting() {
        ValidationTestHelper.testPipelineWithNamingViolation(
                testingPipelineValidator,
                "TESTING",
                false // Should pass
        );
    }

    @Test
    void testPipelineWithIncompleteProcessorInfoPassesInTesting() {
        ValidationTestHelper.testPipelineWithIncompleteProcessorInfo(
                testingPipelineValidator,
                "TESTING",
                false // Should pass
        );
    }

    @Test
    void testPipelineWithSingleStepNoRoutingPassesInTesting() {
        ValidationTestHelper.testPipelineWithSingleStepNoRouting(
                testingPipelineValidator,
                "TESTING"
        );
    }

    @Test
    void testPipelineWithConnectorStepPassesInTesting() {
        ValidationTestHelper.testPipelineWithConnectorStep(
                testingPipelineValidator,
                "TESTING"
        );
    }

    @Test
    void testPipelineWithInvalidTopicNamePassesInTesting() {
        ValidationTestHelper.testPipelineWithInvalidTopicName(
                testingPipelineValidator,
                "TESTING",
                false // Should pass
        );
    }

    @Test
    void testPipelineWithInvalidConsumerGroupPassesInTesting() {
        ValidationTestHelper.testPipelineWithInvalidConsumerGroup(
                testingPipelineValidator,
                "TESTING",
                false // Should pass
        );
    }

    @Test
    void testPipelineWithMismatchedProcessorPassesInTesting() {
        ValidationTestHelper.testPipelineWithMismatchedProcessor(
                testingPipelineValidator,
                "TESTING",
                false // Should pass
        );
    }

    @Test
    void testPipelineWithDisabledRetriesPassesInTesting() {
        ValidationTestHelper.testPipelineWithDisabledRetries(
                testingPipelineValidator,
                "TESTING"
        );
    }

    @Test
    void testPipelineWithUnregisteredServiceFailsInTesting() {
        ValidationTestHelper.testPipelineWithUnregisteredService(
                testingPipelineValidator,
                compositeClusterValidator,
                "TESTING",
                true // Should fail because StepReferenceValidator runs in all modes
        );
    }
    
    @Test
    void testPipelineWithDirectTwoStepLoopPassesInTesting() {
        ValidationTestHelper.testPipelineWithDirectTwoStepLoop(
                testingPipelineValidator,
                compositeClusterValidator,
                "TESTING",
                false // Should pass in TESTING mode (loop detection relaxed)
        );
    }
    
    /**
     * In TESTING mode, the InterPipelineLoopValidator is still run by the CompositeClusterValidator,
     * but we expect it to not report any errors.
     */
    @Test
    void testClusterWithDirectInterPipelineLoopErrorsIgnoredInTesting() {
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createClusterWithDirectInterPipelineLoop();
        
        // Validate the cluster
        ValidationResult result = compositeClusterValidator.validate(clusterConfig);
        
        // The validation might fail, but we expect the errors to be related to other validators, not the loop detection
        assertThat("No loop detection errors should be reported in TESTING mode", 
                result.errors(), not(hasItem(containsString("Detected a loop across pipelines"))));
    }
}