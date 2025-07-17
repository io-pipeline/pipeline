
package io.pipeline.validation;

import io.pipeline.api.model.PipelineClusterConfig;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.Composite;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.InterPipelineLoopValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ProductionValidationTest {

    @Inject
    @Composite
    PipelineConfigValidator compositePipelineValidator;

    @Inject
    InterPipelineLoopValidator interPipelineLoopValidator;

    /**
     * Tests that a cluster with a single empty pipeline is valid in PRODUCTION mode.
     * This is the most basic valid configuration.
     */
    @Test
    void testEmptyClusterIsValidInProduction() {
        // 1. Generate the configuration object
        PipelineClusterConfig clusterConfig = MockPipelineGenerator.createClusterWithEmptyPipeline();

        // 2. Validate the cluster-level configuration
        ValidationResult clusterResult = interPipelineLoopValidator.validate(clusterConfig);
        assertTrue(clusterResult.valid(), "Cluster-level validation should pass.");
        // The placeholder validator currently returns a warning, so we check for that.
        // When the validator is implemented, this should be changed to assertTrue(clusterResult.warnings().isEmpty());
        assertFalse(clusterResult.warnings().isEmpty(), "Expected a warning from the placeholder InterPipelineLoopValidator.");
        assertEquals(1, clusterResult.warnings().size());
        assertEquals("Inter-pipeline loop detection is not yet implemented", clusterResult.warnings().get(0));


        // 3. Validate each pipeline within the cluster
        assertNotNull(clusterConfig.pipelineGraphConfig(), "PipelineGraphConfig should not be null.");
        assertNotNull(clusterConfig.pipelineGraphConfig().pipelines(), "Pipelines map should not be null.");

        for (PipelineConfig pipelineConfig : clusterConfig.pipelineGraphConfig().pipelines().values()) {
            // The composite validator defaults to PRODUCTION mode.
            ValidationResult pipelineResult = compositePipelineValidator.validate(pipelineConfig);

            // An empty pipeline is valid and should have no errors or warnings.
            // The IntraPipelineLoopValidator has a guard clause for empty pipelines.
            assertTrue(pipelineResult.valid(), "Pipeline '" + pipelineConfig.name() + "' should be valid.");
            assertTrue(pipelineResult.warnings().isEmpty(), "An empty pipeline should have no warnings.");
            assertTrue(pipelineResult.errors().isEmpty(), "Pipeline '" + pipelineConfig.name() + "' should have no errors.");
        }
    }

    /**
     * Tests that a pipeline with a naming convention violation (a dot in the name)
     * fails validation in PRODUCTION mode.
     */
    @Test
    void testPipelineWithNamingViolationFailsInProduction() {
        // 1. Generate the invalid configuration
        PipelineConfig invalidConfig = MockPipelineGenerator.createPipelineWithNamingViolation();

        // 2. Validate in PRODUCTION mode
        ValidationResult result = compositePipelineValidator.validate(invalidConfig);

        // 3. Assert that validation fails and contains the correct error
        assertFalse(result.valid(), "Validation should fail for a pipeline with a dot in its name.");
        assertFalse(result.errors().isEmpty(), "Expected at least one validation error.");

        String expectedError = "Pipeline name 'invalid.pipeline.name' cannot contain dots - dots are reserved as delimiters in topic naming convention";
        assertTrue(result.errors().contains(expectedError), "The specific naming convention error should be reported.");
    }
}
