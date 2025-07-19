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
 * Tests for the PRODUCTION validation mode.
 * In PRODUCTION mode:
 * - All validators run
 * - All checks are strict (warnings become errors)
 * - No incomplete configurations allowed
 * - Full naming convention enforcement
 * - Complete processor information required
 * - Loop detection enforced
 */
@QuarkusTest
public class ProductionValidationTest {

    @Inject
    @Composite
    @Named("productionPipelineValidator")
    PipelineConfigValidator productionPipelineValidator;

    @Inject
    CompositeClusterValidator compositeClusterValidator;

    @Test
    void testEmptyClusterIsValidInProduction() {
        ValidationTestHelper.testEmptyClusterIsValid(
                productionPipelineValidator,
                compositeClusterValidator,
                "PRODUCTION"
        );
    }

    @Test
    void testPipelineWithNamingViolationFailsInProduction() {
        ValidationTestHelper.testPipelineWithNamingViolation(
                productionPipelineValidator,
                "PRODUCTION",
                true // Should fail
        );
    }

    @Test
    void testPipelineWithIncompleteProcessorInfoFailsInProduction() {
        ValidationTestHelper.testPipelineWithIncompleteProcessorInfo(
                productionPipelineValidator,
                "PRODUCTION",
                true // Should fail
        );
    }

    @Test
    void testPipelineWithSingleStepNoRoutingFailsInProduction() {
        ValidationTestHelper.testPipelineWithSingleStepNoRouting(
                productionPipelineValidator,
                "PRODUCTION"
        );
    }

    @Test
    void testPipelineWithConnectorStepPassesInProduction() {
        ValidationTestHelper.testPipelineWithConnectorStep(
                productionPipelineValidator,
                "PRODUCTION"
        );
    }

    @Test
    void testPipelineWithInvalidTopicNameFailsInProduction() {
        ValidationTestHelper.testPipelineWithInvalidTopicName(
                productionPipelineValidator,
                "PRODUCTION",
                true // Should fail
        );
    }

    @Test
    void testPipelineWithInvalidConsumerGroupFailsInProduction() {
        ValidationTestHelper.testPipelineWithInvalidConsumerGroup(
                productionPipelineValidator,
                "PRODUCTION",
                true // Should fail
        );
    }

    @Test
    void testPipelineWithMismatchedProcessorPassesInProduction() {
        ValidationTestHelper.testPipelineWithMismatchedProcessor(
                productionPipelineValidator,
                "PRODUCTION",
                false // Should pass now that we've fixed the test data
        );
    }

    @Test
    void testPipelineWithDisabledRetriesPassesInProduction() {
        ValidationTestHelper.testPipelineWithDisabledRetries(
                productionPipelineValidator,
                "PRODUCTION"
        );
    }

    @Test
    void testPipelineWithUnregisteredServiceFailsInProduction() {
        ValidationTestHelper.testPipelineWithUnregisteredService(
                productionPipelineValidator,
                compositeClusterValidator,
                "PRODUCTION",
                true // Should fail
        );
    }
    
    @Test
    void testPipelineWithDirectTwoStepLoopFailsInProduction() {
        ValidationTestHelper.testPipelineWithDirectTwoStepLoop(
                productionPipelineValidator,
                compositeClusterValidator,
                "PRODUCTION",
                true // Should fail now that IntraPipelineLoopValidator is fully implemented
        );
    }
    
    @Test
    void testClusterWithDirectInterPipelineLoopFailsInProduction() {
        ValidationTestHelper.testClusterWithDirectInterPipelineLoop(
                compositeClusterValidator,
                "PRODUCTION",
                true // Should fail in PRODUCTION mode
        );
    }
}