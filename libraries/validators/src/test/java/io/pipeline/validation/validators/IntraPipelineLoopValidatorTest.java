package io.pipeline.validation.validators;

import io.pipeline.model.validation.validators.IntraPipelineLoopValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class IntraPipelineLoopValidatorTest extends IntraPipelineLoopValidatorTestBase {
    
    @Inject
    IntraPipelineLoopValidator validator;
    
    @Override
    protected IntraPipelineLoopValidator getValidator() {
        return validator;
    }
}