package io.pipeline.model.validation.validators;

import io.pipeline.validation.validators.RetryConfigValidatorTestBase;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
public class RetryConfigValidatorIT extends RetryConfigValidatorTestBase {

    private final RetryConfigValidator validator = new RetryConfigValidator();

    @Override
    protected RetryConfigValidator getValidator() {
        return validator;
    }
}