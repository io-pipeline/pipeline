package io.pipeline.validation;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.CompositeValidator;
import io.pipeline.model.validation.validators.ProcessorInfoValidator;
import io.pipeline.model.validation.validators.OutputRoutingValidator;
import io.pipeline.model.validation.validators.RequiredFieldsValidator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ValidationModeTestBase {
    
    protected abstract CompositeValidator<PipelineConfig> getValidator();
    
    @Test
    void testValidatorSupportsModes() {
        // Test that validators can declare supported modes
        ProcessorInfoValidator processorValidator = new ProcessorInfoValidator();
        assertEquals(Collections.singleton(ValidationMode.PRODUCTION), processorValidator.supportedModes());

        OutputRoutingValidator routingValidator = new OutputRoutingValidator();
        assertEquals(Collections.singleton(ValidationMode.PRODUCTION), routingValidator.supportedModes());

        RequiredFieldsValidator requiredValidator = new RequiredFieldsValidator();
        assertEquals(
            Set.of(ValidationMode.PRODUCTION, ValidationMode.DESIGN, ValidationMode.TESTING),
            requiredValidator.supportedModes()
        );
    }
    
    @Test
    void testDesignModeSkipsProductionOnlyValidators() {
        PipelineConfig config = new PipelineConfig("test", null);
        
        // Design mode should only run the all-modes validator
        ValidationResult designResult = getValidator().validate(config, ValidationMode.DESIGN);
        assertTrue(designResult.valid());
        assertFalse(designResult.hasErrors());

        // Production mode should run both validators
        ValidationResult productionResult = getValidator().validate(config, ValidationMode.PRODUCTION);
        assertFalse(productionResult.valid());
        assertTrue(productionResult.hasErrors());
        assertTrue(productionResult.errors().contains("Production-only check failed"));
    }
    
    @Test
    void testTestingModeSkipsProductionOnlyValidators() {
        PipelineConfig config = new PipelineConfig("test", null);
        
        // Testing mode should only run the all-modes validator
        ValidationResult testingResult = getValidator().validate(config, ValidationMode.TESTING);
        assertTrue(testingResult.valid());
        assertFalse(testingResult.hasErrors());
    }
    
    @Test
    void testDefaultModeIsProduction() {
        PipelineConfig config = new PipelineConfig("test", null);
        
        // Default validate() should use PRODUCTION mode
        ValidationResult defaultResult = getValidator().validate(config);
        ValidationResult productionResult = getValidator().validate(config, ValidationMode.PRODUCTION);
        
        assertEquals(productionResult.valid(), defaultResult.valid());
        assertEquals(productionResult.errors(), defaultResult.errors());
    }
}