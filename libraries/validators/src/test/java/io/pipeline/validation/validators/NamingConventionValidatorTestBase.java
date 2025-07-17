package io.pipeline.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.validators.NamingConventionValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class NamingConventionValidatorTestBase {
    
    protected abstract NamingConventionValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        NamingConventionValidator validator = getValidator();
        assertThat(validator.getPriority(), is(200));
        assertThat(validator.getValidatorName(), is("NamingConventionValidator"));
    }
    
    @Test
    void testNullPipelineConfiguration() {
        ValidationResult result = getValidator().validate(null);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
        assertThat(result.warnings(), is(empty()));
    }
    
    @Test
    void testValidPipelineNameConvention() {
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Collections.emptyMap()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
    }
    
    @Test
    void testInvalidPipelineNameWithDots() {
        PipelineConfig config = new PipelineConfig(
            "document.processing",
            Collections.emptyMap()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(false));
        assertThat(result.errors(), hasSize(2));
        assertThat(result.errors().get(0), containsString("cannot contain dots - dots are reserved as delimiters"));
        assertThat(result.errors().get(1), containsString("must contain only alphanumeric characters and hyphens"));
    }
    
    @Test
    void testInvalidPipelineNameWithSpecialCharacters() {
        PipelineConfig config = new PipelineConfig(
            "document_processing@test",
            Collections.emptyMap()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(false));
        assertThat(result.errors(), hasSize(1));
        assertThat(result.errors().getFirst(), containsString("must contain only alphanumeric characters and hyphens"));
    }
    
    @Test
    void testLongPipelineNameWarning() {
        String longName = "a".repeat(51);
        PipelineConfig config = new PipelineConfig(
            longName,
            Collections.emptyMap()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.warnings(), hasSize(1));
        assertThat(result.warnings().getFirst(), containsString("longer than 50 characters"));
    }
    
    @Test
    void testValidStepNameConvention() {
        // Create a minimal ProcessorInfo for testing
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service", 
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "document-parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
    }
    
    @Test
    void testInvalidStepNameWithDots() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "document.parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(false));
        assertThat(result.errors(), hasSize(2));
        assertThat(result.errors().get(0), containsString("cannot contain dots - dots are reserved as delimiters"));
        assertThat(result.errors().get(1), containsString("must contain only alphanumeric characters and hyphens"));
    }
    
    @Test
    void testValidTopicNamingConvention() {
        KafkaTransportConfig kafka = new KafkaTransportConfig(
            "document-processing.parser.input",
            null,
            null,
            null,
            null,
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafka
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            Collections.emptyList(),
            Map.of("default", output),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
        assertThat(result.warnings(), is(empty()));
    }
    
    @Test
    void testInvalidTopicNamingPattern() {
        KafkaTransportConfig kafka = new KafkaTransportConfig(
            "wrong-pattern",
            null,
            null,
            null,
            null,
            null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafka
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            Collections.emptyList(),
            Map.of("default", output),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(false));
        assertThat(result.errors(), hasSize(2));
        assertThat(result.errors().get(0), containsString("doesn't follow the required naming pattern"));
        assertThat(result.errors().get(1), containsString("DLQ topic"));
    }
    
    @Test
    void testValidConsumerGroupNaming() {
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "document-processing.consumer-group",
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
        assertThat(result.warnings(), is(empty()));
    }
    
    @Test
    void testInvalidConsumerGroupNaming() {
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-topic"),
            "wrong-consumer-group",
            null
        );
        
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service",
            null
        );
        
        PipelineStepConfig step = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Test step",
            null,
            null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "document-processing",
            Map.of("step1", step)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(false));
        assertThat(result.errors(), hasSize(1));
        assertThat(result.errors().getFirst(), containsString("doesn't follow the required naming pattern"));
    }
}