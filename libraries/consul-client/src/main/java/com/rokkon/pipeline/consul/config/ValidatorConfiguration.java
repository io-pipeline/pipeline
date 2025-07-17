package com.rokkon.pipeline.consul.config;

import com.rokkon.pipeline.engine.validation.CompositeValidator;
import com.rokkon.pipeline.api.validation.PipelineConfigValidatable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI configuration for validators used in the Consul service.
 */
@ApplicationScoped
public class ValidatorConfiguration {

    /**
     * Default constructor for CDI.
     */
    public ValidatorConfiguration() {
        // Default constructor for CDI
    }

    /**
     * Creates and configures a composite validator for pipeline configurations.
     * This validator combines multiple specialized validators to perform comprehensive validation.
     * 
     * @return A configured CompositeValidator for pipeline configuration validation
     */
    @Produces
    @ApplicationScoped
    public CompositeValidator<PipelineConfigValidatable> pipelineConfigValidator() {
        CompositeValidator<PipelineConfigValidatable> composite = new CompositeValidator<>("PipelineConfigValidator");

        // Add all the validators we need for pipeline configuration
        composite.addValidator(new RequiredFieldsValidator())
                 .addValidator(new NamingConventionValidator())
                 .addValidator(new StepTypeValidator())
                 .addValidator(new IntraPipelineLoopValidator())
                 .addValidator(new StepReferenceValidator())
                 .addValidator(new ProcessorInfoValidator())
                 .addValidator(new TransportConfigValidator())
                 .addValidator(new RetryConfigValidator())
                 .addValidator(new OutputRoutingValidator())
                 .addValidator(new KafkaTopicNamingValidator());

        return composite;
    }
}
