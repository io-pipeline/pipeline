package io.pipeline.validation.validators;

import io.pipeline.model.validation.validators.TransportConfigValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class TransportConfigValidatorTest extends TransportConfigValidatorTestBase {
    
    @Inject
    TransportConfigValidator validator;
    
    @Override
    protected TransportConfigValidator getValidator() {
        return validator;
    }
}