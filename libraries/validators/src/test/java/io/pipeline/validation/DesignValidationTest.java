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

    @Test
    void testEmptyClusterIsValidInDesign() {
        ValidationTestHelper.testEmptyClusterIsValid(
                designPipelineValidator,
                interPipelineLoopValidator,
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
}