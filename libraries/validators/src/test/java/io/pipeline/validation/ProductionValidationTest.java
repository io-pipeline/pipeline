package io.pipeline.validation;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.Composite;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.InterPipelineLoopValidator;
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
    InterPipelineLoopValidator interPipelineLoopValidator;

    @Test
    void testEmptyClusterIsValidInProduction() {
        ValidationTestHelper.testEmptyClusterIsValid(
                productionPipelineValidator,
                interPipelineLoopValidator,
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
}