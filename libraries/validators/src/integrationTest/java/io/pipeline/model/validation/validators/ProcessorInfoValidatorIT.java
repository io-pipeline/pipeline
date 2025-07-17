package io.pipeline.model.validation.validators;

import io.pipeline.validation.validators.ProcessorInfoValidatorTestBase;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class ProcessorInfoValidatorIT extends ProcessorInfoValidatorTestBase {
    
    private ProcessorInfoValidator validator;
    
    @BeforeEach
    void setup() {
        validator = new ProcessorInfoValidator();
    }
    
    @Override
    protected ProcessorInfoValidator getValidator() {
        return validator;
    }
}