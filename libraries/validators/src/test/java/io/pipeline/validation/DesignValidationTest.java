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
    InterPipelineLoopValidator interPipelineLoopValidator;

    /**
     * Tests that a cluster with a single empty pipeline is valid in DESIGN mode.
     * This is the most basic valid configuration.
     */
    @Test
    void testEmptyClusterIsValidInDesign() {
        ValidationTestHelper.testEmptyClusterIsValid(
                designPipelineValidator,
                interPipelineLoopValidator,
                "DESIGN"
        );
    }

    /**
     * Tests that a pipeline with a naming convention violation (a dot in the name)
     * fails validation in DESIGN mode.
     */
    @Test
    void testPipelineWithNamingViolationFailsInDesign() {
        ValidationTestHelper.testPipelineWithNamingViolation(
                designPipelineValidator,
                "DESIGN",
                true // Should fail in DESIGN mode
        );
    }

    /**
     * Tests that a pipeline with incomplete processor information
     * fails validation in DESIGN mode.
     */
    @Test
    void testPipelineWithIncompleteProcessorInfoFailsInDesign() {
        ValidationTestHelper.testPipelineWithIncompleteProcessorInfo(
                designPipelineValidator,
                "DESIGN",
                true // Should fail in DESIGN mode
        );
    }

    /**
     * Tests that warnings are not converted to errors in DESIGN mode.
     * This is a key difference from PRODUCTION mode.
     */
    @Test
    void testWarningsAllowedInDesign() {
        // Create a pipeline with configuration that generates warnings but not errors
        PipelineConfig config = MockPipelineGenerator.createPipelineWithWarnings();
        
        // Validate in DESIGN mode
        ValidationResult result = designPipelineValidator.validate(config);
        
        // In DESIGN mode, warnings should not cause validation to fail
        assertTrue(result.valid(), "Pipeline with warnings should be valid in DESIGN mode");
        
        // But we should still have warnings
        assertFalse(result.warnings().isEmpty(), "Pipeline should have warnings in DESIGN mode");
        
        // Print warnings for debugging
        System.out.println("DESIGN mode warnings: " + result.warnings());
    }
}