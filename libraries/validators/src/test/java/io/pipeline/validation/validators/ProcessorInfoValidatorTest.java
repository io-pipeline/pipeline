package io.pipeline.validation.validators;

import io.pipeline.model.validation.validators.ProcessorInfoValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ProcessorInfoValidatorTest extends ProcessorInfoValidatorTestBase {
    
    @Inject
    ProcessorInfoValidator validator;
    
    @Override
    protected ProcessorInfoValidator getValidator() {
        return validator;
    }
}