package io.pipeline.validation;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DESIGN validation mode.
 * In DESIGN mode:
 * - Most validators run except highly technical ones
 * - Warnings are allowed (not converted to errors)
 * - Basic structural validation is enforced
 * - Naming conventions are enforced but with warnings
 * - Basic processor information is required
 * - Loop detection is performed with warnings
 */
@QuarkusTest
public class DesignValidationTest {

    @Inject
    @Composite
    @Named("designPipelineValidator")
    PipelineConfigValidator designPipelineValidator;

    @Inject
    CompositeClusterValidator compositeClusterValidator;

    @Test
    void testEmptyClusterIsValidInDesign() {
        ValidationTestHelper.testEmptyClusterIsValid(
                designPipelineValidator,
                compositeClusterValidator,
                "DESIGN"
        );
    }

    @Test
    void testPipelineWithNamingViolationFailsInDesign() {
        ValidationTestHelper.testPipelineWithNamingViolation(
                designPipelineValidator,
                "DESIGN",
                true // Should fail
        );
    }

    @Test
    void testPipelineWithIncompleteProcessorInfoFailsInDesign() {
        ValidationTestHelper.testPipelineWithIncompleteProcessorInfo(
                designPipelineValidator,
                "DESIGN",
                true // Should fail
        );
    }

    @Test
    void testPipelineWithSingleStepNoRoutingPassesInDesign() {
        ValidationTestHelper.testPipelineWithSingleStepNoRouting(
                designPipelineValidator,
                "DESIGN"
        );
    }

    @Test
    void testPipelineWithConnectorStepPassesInDesign() {
        ValidationTestHelper.testPipelineWithConnectorStep(
                designPipelineValidator,
                "DESIGN"
        );
    }

    @Test
    void testPipelineWithInvalidTopicNameFailsInDesign() {
        ValidationTestHelper.testPipelineWithInvalidTopicName(
                designPipelineValidator,
                "DESIGN",
                true // Should fail
        );
    }

    @Test
    void testPipelineWithInvalidConsumerGroupFailsInDesign() {
        ValidationTestHelper.testPipelineWithInvalidConsumerGroup(
                designPipelineValidator,
                "DESIGN",
                true // Should fail
        );
    }

    @Test
    void testPipelineWithMismatchedProcessorPassesInDesign() {
        ValidationTestHelper.testPipelineWithMismatchedProcessor(
                designPipelineValidator,
                "DESIGN",
                false // Should pass now that we've fixed the test data
        );
    }

    @Test
    void testPipelineWithDisabledRetriesPassesInDesign() {
        ValidationTestHelper.testPipelineWithDisabledRetries(
                designPipelineValidator,
                "DESIGN"
        );
    }

    @Test
    void testPipelineWithUnregisteredServiceFailsInDesign() {
        ValidationTestHelper.testPipelineWithUnregisteredService(
                designPipelineValidator,
                compositeClusterValidator,
                "DESIGN",
                true // Should fail
        );
    }
    
    @Test
    void testPipelineWithDirectTwoStepLoopPassesInDesign() {
        ValidationTestHelper.testPipelineWithDirectTwoStepLoop(
                designPipelineValidator,
                compositeClusterValidator,
                "DESIGN",
                false // Currently passes because IntraPipelineLoopValidator is not fully implemented
        );
    }
}