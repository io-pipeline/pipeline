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

    /**
     * Tests that a cluster with a single empty pipeline is valid in PRODUCTION mode.
     * This is the most basic valid configuration.
     */
    @Test
    void testEmptyClusterIsValidInProduction() {
        ValidationTestHelper.testEmptyClusterIsValid(
                productionPipelineValidator,
                interPipelineLoopValidator,
                "PRODUCTION"
        );
    }

    /**
     * Tests that a pipeline with a naming convention violation (a dot in the name)
     * fails validation in PRODUCTION mode.
     */
    @Test
    void testPipelineWithNamingViolationFailsInProduction() {
        ValidationTestHelper.testPipelineWithNamingViolation(
                productionPipelineValidator,
                "PRODUCTION",
                true // Should fail in PRODUCTION mode
        );
    }

    /**
     * Tests that a pipeline with incomplete processor information
     * fails validation in PRODUCTION mode.
     */
    @Test
    void testPipelineWithIncompleteProcessorInfoFailsInProduction() {
        ValidationTestHelper.testPipelineWithIncompleteProcessorInfo(
                productionPipelineValidator,
                "PRODUCTION",
                true // Should fail in PRODUCTION mode
        );
    }

    /**
     * Tests that warnings are converted to errors in PRODUCTION mode.
     * This is a key difference from DESIGN and TESTING modes.
     */
    @Test
    void testWarningsConvertedToErrorsInProduction() {
        // Create a pipeline with configuration that generates warnings but not errors
        PipelineConfig config = MockPipelineGenerator.createPipelineWithWarnings();
        
        // Validate in PRODUCTION mode
        ValidationResult result = productionPipelineValidator.validate(config);
        
        // In PRODUCTION mode, warnings should cause validation to fail
        assertFalse(result.valid(), "Pipeline with warnings should fail validation in PRODUCTION mode");
        
        // Warnings should be converted to errors
        assertFalse(result.errors().isEmpty(), "Pipeline should have errors in PRODUCTION mode");
        
        // Print errors for debugging
        System.out.println("PRODUCTION mode errors: " + result.errors());
    }
}