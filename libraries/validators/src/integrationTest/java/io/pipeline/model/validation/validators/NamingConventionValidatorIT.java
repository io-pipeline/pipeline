package io.pipeline.model.validation.validators;

import io.pipeline.validation.validators.NamingConventionValidatorTestBase;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

@QuarkusIntegrationTest
public class NamingConventionValidatorIT extends NamingConventionValidatorTestBase {

    private NamingConventionValidator validator;

    @BeforeEach
    void setup() {
        validator = new NamingConventionValidator();
    }

    @Override
    protected NamingConventionValidator getValidator() {
        return validator;
    }
}