package io.pipeline.model.validation.validators;

import com.rokkon.pipeline.validation.validators.OutputRoutingValidatorTestBase;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
public class OutputRoutingValidatorIT extends OutputRoutingValidatorTestBase {

    private final OutputRoutingValidator validator = new OutputRoutingValidator();

    @Override
    protected OutputRoutingValidator getValidator() {
        return validator;
    }
}