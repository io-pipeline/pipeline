package io.pipeline.validation.validators;

import io.pipeline.model.validation.validators.InterPipelineLoopValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class InterPipelineLoopValidatorTest extends InterPipelineLoopValidatorTestBase {
    
    @Inject
    InterPipelineLoopValidator validator;
    
    @Override
    protected InterPipelineLoopValidator getValidator() {
        return validator;
    }
}