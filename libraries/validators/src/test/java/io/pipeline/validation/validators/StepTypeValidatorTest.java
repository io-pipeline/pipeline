package io.pipeline.validation.validators;

import io.pipeline.model.validation.validators.StepTypeValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class StepTypeValidatorTest extends StepTypeValidatorTestBase {
    
    @Inject
    StepTypeValidator validator;
    
    @Override
    protected StepTypeValidator getValidator() {
        return validator;
    }
}