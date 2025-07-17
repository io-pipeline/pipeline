package io.pipeline.model.validation;

import io.pipeline.api.validation.Composite;
import io.pipeline.api.validation.ConfigValidator;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.model.validation.validators.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.List;

/**
 * Producer for the composite PipelineConfigValidator that aggregates all individual validators.
 * Provides three distinct validators for different validation modes:
 * - PRODUCTION: Full validation suite, no errors or warnings allowed
 * - DESIGN: Structural and logical validation, warnings allowed
 * - TESTING: Minimal validation, allows incomplete pipelines
 */
@ApplicationScoped
public class PipelineValidatorProducer {
    
    @Inject
    RequiredFieldsValidator requiredFieldsValidator;
    @Inject
    NamingConventionValidator namingConventionValidator;
    @Inject
    StepReferenceValidator stepReferenceValidator;
    @Inject
    ProcessorInfoValidator processorInfoValidator;
    @Inject
    RetryConfigValidator retryConfigValidator;
    @Inject 
    TransportConfigValidator transportConfigValidator;
    @Inject
    OutputRoutingValidator outputRoutingValidator;
    @Inject
    KafkaTopicNamingValidator kafkaTopicNamingValidator;
    @Inject
    IntraPipelineLoopValidator intraPipelineLoopValidator;
    @Inject
    StepTypeValidator stepTypeValidator;
    
    /**
     * For backward compatibility - delegates to production validator
     */
    @Produces
    @Composite
    @Named("compositePipelineValidator")
    @ApplicationScoped
    public PipelineConfigValidator producePipelineConfigValidator() {
        return produceProductionPipelineConfigValidator();
    }
    
    /**
     * Produces a validator for PRODUCTION mode.
     * All validators run with strict validation (warnings become errors).
     */
    @Produces
    @Composite
    @Named("productionPipelineValidator")
    @ApplicationScoped
    public PipelineConfigValidator produceProductionPipelineConfigValidator() {
        List<ConfigValidator<PipelineConfigValidatable>> validatorList = new ArrayList<>();
        
        // Add all validators - CompositeValidator will filter based on supportedModes()
        validatorList.add(requiredFieldsValidator);
        validatorList.add(namingConventionValidator);
        validatorList.add(stepReferenceValidator);
        validatorList.add(processorInfoValidator);
        validatorList.add(retryConfigValidator);
        validatorList.add(transportConfigValidator);
        validatorList.add(outputRoutingValidator);
        validatorList.add(kafkaTopicNamingValidator);
        validatorList.add(intraPipelineLoopValidator);
        validatorList.add(stepTypeValidator);
        
        // Create and return a concrete implementation with PRODUCTION mode
        return new ModePipelineConfigValidator(validatorList, ValidationMode.PRODUCTION);
    }
    
    /**
     * Produces a validator for DESIGN mode.
     * Most validators run except highly technical ones, warnings allowed.
     */
    @Produces
    @Composite
    @Named("designPipelineValidator")
    @ApplicationScoped
    public PipelineConfigValidator produceDesignPipelineConfigValidator() {
        List<ConfigValidator<PipelineConfigValidatable>> validatorList = new ArrayList<>();
        
        // Add all validators - CompositeValidator will filter based on supportedModes()
        validatorList.add(requiredFieldsValidator);
        validatorList.add(namingConventionValidator);
        validatorList.add(stepReferenceValidator);
        validatorList.add(processorInfoValidator);
        validatorList.add(retryConfigValidator);
        validatorList.add(transportConfigValidator);
        validatorList.add(outputRoutingValidator);
        validatorList.add(kafkaTopicNamingValidator);
        validatorList.add(intraPipelineLoopValidator);
        validatorList.add(stepTypeValidator);
        
        // Create and return a concrete implementation with DESIGN mode
        return new ModePipelineConfigValidator(validatorList, ValidationMode.DESIGN);
    }
    
    /**
     * Produces a validator for TESTING mode.
     * Only essential validators run, many warnings ignored.
     */
    @Produces
    @Composite
    @Named("testingPipelineValidator")
    @ApplicationScoped
    public PipelineConfigValidator produceTestingPipelineConfigValidator() {
        List<ConfigValidator<PipelineConfigValidatable>> validatorList = new ArrayList<>();
        
        // Add all validators - CompositeValidator will filter based on supportedModes()
        validatorList.add(requiredFieldsValidator);
        validatorList.add(namingConventionValidator);
        validatorList.add(stepReferenceValidator);
        validatorList.add(processorInfoValidator);
        validatorList.add(retryConfigValidator);
        validatorList.add(transportConfigValidator);
        validatorList.add(outputRoutingValidator);
        validatorList.add(kafkaTopicNamingValidator);
        validatorList.add(intraPipelineLoopValidator);
        validatorList.add(stepTypeValidator);
        
        // Create and return a concrete implementation with TESTING mode
        return new ModePipelineConfigValidator(validatorList, ValidationMode.TESTING);
    }
}