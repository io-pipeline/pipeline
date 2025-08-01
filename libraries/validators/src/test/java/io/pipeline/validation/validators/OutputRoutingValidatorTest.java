package io.pipeline.validation.validators;

import io.pipeline.model.validation.validators.OutputRoutingValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class OutputRoutingValidatorTest extends OutputRoutingValidatorTestBase {

    @Inject
    OutputRoutingValidator validator;

    @Override
    protected OutputRoutingValidator getValidator() {
        return validator;
    }
}