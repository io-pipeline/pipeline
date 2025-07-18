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
    InterPipelineLoopValidator interPipelineLoopValidator;

    /**
     * Tests that a cluster with a single empty pipeline is valid in TESTING mode.
     * This is the most basic valid configuration.
     */
    @Test
    void testEmptyClusterIsValidInTesting() {
        ValidationTestHelper.testEmptyClusterIsValid(
                testingPipelineValidator,
                interPipelineLoopValidator,
                "TESTING"
        );
    }

    /**
     * Tests that a pipeline with a naming convention violation (a dot in the name)
     * passes validation in TESTING mode, unlike in PRODUCTION and DESIGN modes.
     */
    @Test
    void testPipelineWithNamingViolationPassesInTesting() {
        ValidationTestHelper.testPipelineWithNamingViolation(
                testingPipelineValidator,
                "TESTING",
                false // Should pass in TESTING mode
        );
    }

    /**
     * Tests that a pipeline with incomplete processor information
     * passes validation in TESTING mode, unlike in PRODUCTION and DESIGN modes.
     */
    @Test
    void testPipelineWithIncompleteProcessorInfoPassesInTesting() {
        ValidationTestHelper.testPipelineWithIncompleteProcessorInfo(
                testingPipelineValidator,
                "TESTING",
                false // Should pass in TESTING mode
        );
    }

    /**
     * Tests that a pipeline with schema violations still passes basic structural validation
     * in TESTING mode, which is more permissive than other modes.
     */
    @Test
    void testPipelineWithSchemaViolationPassesBasicValidationInTesting() {
        // Create a pipeline with a schema violation
        PipelineConfig config = MockPipelineGenerator.createPipelineWithSchemaViolation();
        
        // Validate in TESTING mode
        ValidationResult result = testingPipelineValidator.validate(config);
        
        // In TESTING mode, basic structural validation should still pass
        assertTrue(result.valid(), "Pipeline with schema violations should still pass basic validation in TESTING mode");
    }
    
    /**
     * Tests that a simple linear pipeline is valid in TESTING mode.
     * This tests that the basic pipeline structure validation works in TESTING mode.
     */
    @Test
    void testSimpleLinearPipelineIsValidInTesting() {
        // Create a simple linear pipeline
        PipelineConfig config = MockPipelineGenerator.createSimpleLinearPipeline();
        
        // Validate in TESTING mode
        ValidationResult result = testingPipelineValidator.validate(config);
        
        // Should be valid in TESTING mode
        assertTrue(result.valid(), "Simple linear pipeline should be valid in TESTING mode");
        
        // Should have fewer warnings than in other modes
        // (This is a bit of a fuzzy assertion, but it's a reasonable expectation)
        assertTrue(result.warnings().size() <= 1, 
                "TESTING mode should have fewer warnings than other modes");
    }
    
    /**
     * Tests that warnings are ignored in TESTING mode.
     * This is a key difference from both PRODUCTION mode (where warnings become errors)
     * and DESIGN mode (where warnings are reported but don't cause validation to fail).
     */
    @Test
    void testWarningsIgnoredInTesting() {
        // Create a pipeline with configuration that generates warnings but not errors
        PipelineConfig config = MockPipelineGenerator.createPipelineWithWarnings();
        
        // Validate in TESTING mode
        ValidationResult result = testingPipelineValidator.validate(config);
        
        // In TESTING mode, warnings should not cause validation to fail
        assertTrue(result.valid(), "Pipeline with warnings should be valid in TESTING mode");
        
        // In TESTING mode, many warnings are ignored completely
        // Print warnings for debugging
        System.out.println("TESTING mode warnings: " + result.warnings());
    }
}