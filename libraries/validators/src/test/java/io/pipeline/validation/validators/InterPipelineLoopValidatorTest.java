package io.pipeline.validation.validators;

import io.pipeline.model.validation.validators.InterPipelineLoopValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Tests for the InterPipelineLoopValidator.
 * Extends the InterPipelineLoopValidatorTestBase to inherit all test cases.
 */
@QuarkusTest
public class InterPipelineLoopValidatorTest extends InterPipelineLoopValidatorTestBase {
    
    @Inject
    InterPipelineLoopValidator validator;
    
    @Override
    protected InterPipelineLoopValidator getValidator() {
        return validator;
    }
}