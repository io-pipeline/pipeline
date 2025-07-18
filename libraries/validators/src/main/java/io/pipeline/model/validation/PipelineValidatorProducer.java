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

import org.jboss.logging.Logger;

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
    
    private static final Logger LOG = Logger.getLogger(PipelineValidatorProducer.class);
    
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
     * Returns the concrete implementation type for direct injection.
     */
    @Produces
    @Composite
    @Named("productionPipelineValidator")
    @ApplicationScoped
    public ProductionPipelineConfigValidator produceProductionPipelineConfigValidator() {
        LOG.info("Producing PRODUCTION pipeline config validator");
        List<ConfigValidator<PipelineConfigValidatable>> validatorList = new ArrayList<>();
        
        // Add all validators - CompositeValidator will filter based on supportedModes()
        LOG.info("Adding RequiredFieldsValidator");
        validatorList.add(requiredFieldsValidator);
        LOG.info("Adding NamingConventionValidator");
        validatorList.add(namingConventionValidator);
        LOG.info("Adding StepReferenceValidator");
        validatorList.add(stepReferenceValidator);
        LOG.info("Adding ProcessorInfoValidator");
        validatorList.add(processorInfoValidator);
        LOG.info("Adding RetryConfigValidator");
        validatorList.add(retryConfigValidator);
        LOG.info("Adding TransportConfigValidator");
        validatorList.add(transportConfigValidator);
        LOG.info("Adding OutputRoutingValidator");
        validatorList.add(outputRoutingValidator);
        LOG.info("Adding KafkaTopicNamingValidator");
        validatorList.add(kafkaTopicNamingValidator);
        LOG.info("Adding IntraPipelineLoopValidator");
        validatorList.add(intraPipelineLoopValidator);
        LOG.info("Adding StepTypeValidator");
        validatorList.add(stepTypeValidator);
        
        // Create and return a concrete implementation for PRODUCTION mode
        return new ProductionPipelineConfigValidator(validatorList);
    }
    
    /**
     * Produces a validator for PRODUCTION mode as the interface type.
     * This method is for backward compatibility and generic injection.
     */
    @Produces
    @Composite
    @Named("productionPipelineValidatorInterface")
    @ApplicationScoped
    public PipelineConfigValidator produceProductionPipelineConfigValidatorInterface() {
        return produceProductionPipelineConfigValidator();
    }
    
    /**
     * Produces a validator for DESIGN mode.
     * Most validators run except highly technical ones, warnings allowed.
     * Returns the concrete implementation type for direct injection.
     */
    @Produces
    @Composite
    @Named("designPipelineValidator")
    @ApplicationScoped
    public DesignPipelineConfigValidator produceDesignPipelineConfigValidator() {
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
        
        // Create and return a concrete implementation for DESIGN mode
        return new DesignPipelineConfigValidator(validatorList);
    }
    
    /**
     * Produces a validator for DESIGN mode as the interface type.
     * This method is for backward compatibility and generic injection.
     */
    @Produces
    @Composite
    @Named("designPipelineValidatorInterface")
    @ApplicationScoped
    public PipelineConfigValidator produceDesignPipelineConfigValidatorInterface() {
        return produceDesignPipelineConfigValidator();
    }
    
    /**
     * Produces a validator for TESTING mode.
     * Only essential validators run, many warnings ignored.
     * Returns the concrete implementation type for direct injection.
     */
    @Produces
    @Composite
    @Named("testingPipelineValidator")
    @ApplicationScoped
    public TestingPipelineConfigValidator produceTestingPipelineConfigValidator() {
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
        
        // Create and return a concrete implementation for TESTING mode
        return new TestingPipelineConfigValidator(validatorList);
    }
    
    /**
     * Produces a validator for TESTING mode as the interface type.
     * This method is for backward compatibility and generic injection.
     */
    @Produces
    @Composite
    @Named("testingPipelineValidatorInterface")
    @ApplicationScoped
    public PipelineConfigValidator produceTestingPipelineConfigValidatorInterface() {
        return produceTestingPipelineConfigValidator();
    }
}