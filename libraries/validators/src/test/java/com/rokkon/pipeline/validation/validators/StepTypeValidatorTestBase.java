package com.rokkon.pipeline.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.validators.StepTypeValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class StepTypeValidatorTestBase {
    
    protected abstract StepTypeValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        StepTypeValidator validator = getValidator();
        assertThat(validator.getPriority(), is(300));
        assertThat(validator.getValidatorName(), is("StepTypeValidator"));
    }
    
    @Test
    void testNullPipelineConfiguration() {
        ValidationResult result = getValidator().validate(null);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
        assertThat(result.warnings(), is(empty()));
    }
    
    @Test
    void testEmptyPipelineSteps() {
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Collections.emptyMap()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
        assertThat(result.warnings(), is(empty()));
    }
    
    @Test
    void testValidPipelineStructure() {
        // Create CONNECTOR step
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "parser-service",
            null
        );
        
        KafkaTransportConfig kafkaOutput = new KafkaTransportConfig(
            "test-pipeline.parser.input",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "parser-step",
            TransportType.KAFKA,
            null,
            kafkaOutput
        );
        
        PipelineStepConfig initialStep = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Reads documents",
            null,
            null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        // Create PIPELINE step
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-pipeline.parser.input"),
            "test-pipeline.consumer-group",
            null
        );
        
        KafkaTransportConfig kafkaOutput2 = new KafkaTransportConfig(
            "test-pipeline.enricher.input",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output2 = new PipelineStepConfig.OutputTarget(
            "enricher-step",
            TransportType.KAFKA,
            null,
            kafkaOutput2
        );
        
        PipelineStepConfig pipelineStep = new PipelineStepConfig(
            "parser",
            StepType.PIPELINE,
            "Parses documents",
            null,
            null,
            List.of(kafkaInput),
            Map.of("default", output2),
            null, null, null, null, null,
            processorInfo
        );
        
        // Create SINK step
        KafkaInputDefinition kafkaInput2 = new KafkaInputDefinition(
            List.of("test-pipeline.enricher.input"),
            "test-pipeline.consumer-group",
            null
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writes to storage",
            null,
            null,
            List.of(kafkaInput2),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of(
                "reader", initialStep,
                "parser", pipelineStep,
                "writer", sinkStep
            )
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
    }
    
    @Test
    void testMissingInitialPipelineStep() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        // Only PIPELINE and SINK steps
        PipelineStepConfig pipelineStep = new PipelineStepConfig(
            "processor",
            StepType.PIPELINE,
            "Processes data",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writes data",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("processor", pipelineStep, "writer", sinkStep)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));  // Now it's valid, just with warnings
        // We get warnings about missing CONNECTOR step plus warnings about steps without proper inputs/outputs
        assertThat(result.warnings(), hasItem("Pipeline has no CONNECTOR step - data must come from external sources"));
    }
    
    @Test
    void testMissingSinkStep() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        KafkaTransportConfig kafkaOutput = new KafkaTransportConfig(
            "test-pipeline.output",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaOutput
        );
        
        // Only CONNECTOR step
        PipelineStepConfig initialStep = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Reads data",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("reader", initialStep)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));  // Now it's valid, just with warnings
        // We get warnings about missing SINK step plus warnings about steps without proper inputs/outputs
        assertThat(result.warnings(), hasItem("Pipeline has no SINK step - ensure data has a destination"));
    }
    
    @Test
    void testMultipleInitialPipelineSteps() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        KafkaTransportConfig kafkaOutput = new KafkaTransportConfig(
            "test-pipeline.output",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaOutput
        );
        
        PipelineStepConfig initialStep1 = new PipelineStepConfig(
            "reader1",
            StepType.CONNECTOR,
            "First reader",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig initialStep2 = new PipelineStepConfig(
            "reader2",
            StepType.CONNECTOR,
            "Second reader",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writer",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of(
                "reader1", initialStep1,
                "reader2", initialStep2,
                "writer", sinkStep
            )
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));  // Now it's valid, just with warnings
        // We get warnings about multiple CONNECTOR steps
        assertThat(result.warnings(), hasItem("Pipeline has multiple CONNECTOR steps (2) - consider if this is intended"));
    }
    
    @Test
    void testInitialPipelineWithKafkaInputs() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("some-topic"),
            "consumer-group",
            null
        );
        
        KafkaTransportConfig kafkaOutput = new KafkaTransportConfig(
            "test-pipeline.output",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaOutput
        );
        
        PipelineStepConfig initialStep = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Reader with inputs",
            null, null,
            List.of(kafkaInput), // Should not have inputs
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writer",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("reader", initialStep, "writer", sinkStep)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(false));
        assertThat(result.errors(), hasSize(1));
        assertThat(result.errors().getFirst(), containsString("Step 'reader': CONNECTOR steps should not have Kafka inputs"));
    }
    
    @Test
    void testInitialPipelineWithoutOutputs() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        PipelineStepConfig initialStep = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Reader without outputs",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(), // No outputs
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writer",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("reader", initialStep, "writer", sinkStep)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(false));
        assertThat(result.errors(), hasSize(1));
        assertThat(result.errors().getFirst(), containsString("Step 'reader': CONNECTOR steps must have at least one output"));
    }
    
    @Test
    void testSinkStepWithOutputs() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        KafkaTransportConfig kafkaOutput = new KafkaTransportConfig(
            "test-pipeline.output",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaOutput
        );
        
        PipelineStepConfig initialStep = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Reader",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-pipeline.output"),
            "consumer-group",
            null
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writer with outputs",
            null, null,
            List.of(kafkaInput),
            Map.of("default", output), // Should not have outputs
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("reader", initialStep, "writer", sinkStep)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(false));
        assertThat(result.errors(), hasSize(1));
        assertThat(result.errors().getFirst(), containsString("Step 'writer': SINK steps should not have outputs"));
    }
    
    @Test
    void testSinkStepWithoutInputsWarning() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        KafkaTransportConfig kafkaOutput = new KafkaTransportConfig(
            "test-pipeline.output",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaOutput
        );
        
        PipelineStepConfig initialStep = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Reader",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writer without inputs",
            null, null,
            Collections.emptyList(), // No inputs
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("reader", initialStep, "writer", sinkStep)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
        assertThat(result.warnings(), hasSize(1));
        assertThat(result.warnings().getFirst(), containsString("Step 'writer': SINK steps typically have inputs to process"));
    }
    
    @Test
    void testPipelineStepWarnings() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service",
            null
        );
        
        KafkaTransportConfig kafkaOutput = new KafkaTransportConfig(
            "test-pipeline.output",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget output = new PipelineStepConfig.OutputTarget(
            "next-step",
            TransportType.KAFKA,
            null,
            kafkaOutput
        );
        
        PipelineStepConfig initialStep = new PipelineStepConfig(
            "reader",
            StepType.CONNECTOR,
            "Reader",
            null, null,
            Collections.emptyList(),
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        // Pipeline step without inputs
        PipelineStepConfig pipelineStep1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor without inputs",
            null, null,
            Collections.emptyList(), // No inputs
            Map.of("default", output),
            null, null, null, null, null,
            processorInfo
        );
        
        // Pipeline step without outputs
        KafkaInputDefinition kafkaInput = new KafkaInputDefinition(
            List.of("test-pipeline.output"),
            "consumer-group",
            null
        );
        
        PipelineStepConfig pipelineStep2 = new PipelineStepConfig(
            "processor2",
            StepType.PIPELINE,
            "Processor without outputs",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(), // No outputs
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig sinkStep = new PipelineStepConfig(
            "writer",
            StepType.SINK,
            "Writer",
            null, null,
            List.of(kafkaInput),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of(
                "reader", initialStep,
                "processor1", pipelineStep1,
                "processor2", pipelineStep2,
                "writer", sinkStep
            )
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat(result.valid(), is(true));
        assertThat(result.errors(), is(empty()));
        assertThat(result.warnings(), hasSize(2));
        assertThat(result.warnings(), containsInAnyOrder(
            "Step 'processor1': PIPELINE steps typically have inputs",
            "Step 'processor2': PIPELINE steps typically have outputs"
        ));
    }
}

