package io.pipeline.model.validation.validators;

import io.pipeline.validation.validators.TransportConfigValidatorTestBase;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class TransportConfigValidatorIT extends TransportConfigValidatorTestBase {
    
    private TransportConfigValidator validator;
    
    @BeforeEach
    void setup() {
        validator = new TransportConfigValidator();
    }
    
    @Override
    protected TransportConfigValidator getValidator() {
        return validator;
    }
}