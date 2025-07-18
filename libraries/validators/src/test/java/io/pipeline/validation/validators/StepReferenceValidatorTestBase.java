package io.pipeline.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.model.validation.validators.StepReferenceValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class StepReferenceValidatorTestBase {
    
    protected abstract StepReferenceValidator getValidator();
    
    @Test
    void testValidatorPriorityAndName() {
        StepReferenceValidator validator = getValidator();
        assertThat("Validator priority should be 400, but was: " + validator.getPriority(), 
                  validator.getPriority(), is(400));
        assertThat("Validator name should be 'StepReferenceValidator', but was: '" + validator.getValidatorName() + "'", 
                  validator.getValidatorName(), is("StepReferenceValidator"));
    }
    
    @Test
    void testNullPipelineConfiguration() {
        ValidationResult result = getValidator().validate(null);
        assertThat("Validation result should be valid for null configuration, but was: " + result.valid(), 
                  result.valid(), is(true));
        assertThat("Validation errors should be empty for null configuration, but found: " + result.errors(), 
                  result.errors(), is(empty()));
        assertThat("Validation warnings should be empty for null configuration, but found: " + result.warnings(), 
                  result.warnings(), is(empty()));
    }
    
    @Test
    void testEmptyPipelineSteps() {
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Collections.emptyMap()
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat("Validation result should be valid for empty pipeline steps, but was: " + result.valid(), 
                  result.valid(), is(true));
        assertThat("Validation errors should be empty for empty pipeline steps, but found: " + result.errors(), 
                  result.errors(), is(empty()));
        assertThat("Validation warnings should be empty for empty pipeline steps, but found: " + result.warnings(), 
                  result.warnings(), is(empty()));
    }
    
    @Test
    void testValidInternalGrpcReferences() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        // Create steps with internal gRPC references
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "step2", // References another step in the pipeline
            null
        );
        
        PipelineStepConfig.OutputTarget grpcOutput = new PipelineStepConfig.OutputTarget(
            "step2",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "First processor",
            null, null,
            Collections.emptyList(),
            Map.of("default", grpcOutput),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
            "processor2",
            StepType.PIPELINE,
            "Second processor",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat("Validation result should be valid for valid internal gRPC references, but was: " + result.valid() + 
                  "\nFull validation result: " + result, 
                  result.valid(), is(true));
        assertThat("Validation errors should be empty for valid internal gRPC references, but found: " + result.errors(), 
                  result.errors(), is(empty()));
    }
    
    @Test
    void testInvalidInternalGrpcReference() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        // Create step with reference to non-existent step
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "nonexistent", // References a step that doesn't exist
            null
        );
        
        PipelineStepConfig.OutputTarget grpcOutput = new PipelineStepConfig.OutputTarget(
            "nonexistent",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor with bad reference",
            null, null,
            Collections.emptyList(),
            Map.of("default", grpcOutput),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat("Validation result should be invalid for non-existent target step, but was: " + result.valid() + 
                  "\nFull validation result: " + result, 
                  result.valid(), is(false));
        assertThat("Validation should have exactly 1 error for non-existent target step, but found: " + result.errors().size() + " errors" + 
                  "\nActual errors: " + result.errors(), 
                  result.errors(), hasSize(1));
        assertThat("Error message should mention the non-existent target step. Actual errors: " + result.errors(), 
                  result.errors().getFirst(), containsString("Step 'step1' output 'default' references non-existent target step 'nonexistent'"));
    }
    
    @Test
    void testExternalGrpcReferenceIgnored() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        // Create step with external gRPC reference (contains dots, so it's a FQDN)
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig(
            "external.service.com", // External service - should be ignored
            null
        );
        
        PipelineStepConfig.OutputTarget grpcOutput = new PipelineStepConfig.OutputTarget(
            "external-service",
            TransportType.GRPC,
            grpcTransport,
            null
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor with external reference",
            null, null,
            Collections.emptyList(),
            Map.of("default", grpcOutput),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat("Validation result should be valid for external gRPC references, but was: " + result.valid() + 
                  "\nFull validation result: " + result, 
                  result.valid(), is(true));
        assertThat("Validation errors should be empty for external gRPC references, but found: " + result.errors(), 
                  result.errors(), is(empty()));
    }
    
    @Test
    void testDuplicateStepNames() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        // Create two steps with the same stepName
        PipelineStepConfig step1 = new PipelineStepConfig(
            "duplicate-name", // Same step name
            StepType.PIPELINE,
            "First processor",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineStepConfig step2 = new PipelineStepConfig(
            "duplicate-name", // Same step name
            StepType.PIPELINE,
            "Second processor",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "step2", step2)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat("Validation result should be invalid for duplicate step names, but was: " + result.valid() + 
                  "\nFull validation result: " + result, 
                  result.valid(), is(false));
        assertThat("Validation should have exactly 1 error for duplicate step names, but found: " + result.errors().size() + " errors" + 
                  "\nActual errors: " + result.errors(), 
                  result.errors(), hasSize(1));
        assertThat("Error message should mention the duplicate step name. Actual errors: " + result.errors(), 
                  result.errors().get(0), containsString("Duplicate step name found: duplicate-name"));
    }
    
    @Test
    void testKafkaTransportIgnored() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        // Create step with Kafka transport (should be ignored by this validator)
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
            "some.topic",
            null, null, null, null, null
        );
        
        PipelineStepConfig.OutputTarget kafkaOutput = new PipelineStepConfig.OutputTarget(
            "kafka-target",
            TransportType.KAFKA,
            null,
            kafkaTransport
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor with Kafka output",
            null, null,
            Collections.emptyList(),
            Map.of("default", kafkaOutput),
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat("Validation result should be valid for Kafka transport (ignored), but was: " + result.valid() + 
                  "\nFull validation result: " + result, 
                  result.valid(), is(true));
        assertThat("Validation errors should be empty for Kafka transport (ignored), but found: " + result.errors(), 
                  result.errors(), is(empty()));
    }
    
    @Test
    void testMultipleOutputsWithMixedReferences() {
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "service"
        );
        
        // Create valid target step
        PipelineStepConfig targetStep = new PipelineStepConfig(
            "target",
            StepType.PIPELINE,
            "Target processor",
            null, null,
            Collections.emptyList(),
            Collections.emptyMap(),
            null, null, null, null, null,
            processorInfo
        );
        
        // Create step with multiple outputs
        GrpcTransportConfig validGrpc = new GrpcTransportConfig("target", null);
        GrpcTransportConfig invalidGrpc = new GrpcTransportConfig("invalid", null);
        GrpcTransportConfig externalGrpc = new GrpcTransportConfig("external.service.com", null);
        
        Map<String, PipelineStepConfig.OutputTarget> outputs = Map.of(
            "valid", new PipelineStepConfig.OutputTarget("target", TransportType.GRPC, validGrpc, null),
            "invalid", new PipelineStepConfig.OutputTarget("invalid", TransportType.GRPC, invalidGrpc, null),
            "external", new PipelineStepConfig.OutputTarget("external", TransportType.GRPC, externalGrpc, null)
        );
        
        PipelineStepConfig step1 = new PipelineStepConfig(
            "processor1",
            StepType.PIPELINE,
            "Processor with multiple outputs",
            null, null,
            Collections.emptyList(),
            outputs,
            null, null, null, null, null,
            processorInfo
        );
        
        PipelineConfig config = new PipelineConfig(
            "test-pipeline",
            Map.of("step1", step1, "target", targetStep)
        );
        
        ValidationResult result = getValidator().validate(config);
        assertThat("Validation result should be invalid for mixed references with invalid reference, but was: " + result.valid() + 
                  "\nFull validation result: " + result, 
                  result.valid(), is(false));
        assertThat("Validation should have exactly 1 error for invalid reference, but found: " + result.errors().size() + " errors" + 
                  "\nActual errors: " + result.errors(), 
                  result.errors(), hasSize(1));
        assertThat("Error message should mention the non-existent target step. Actual errors: " + result.errors(), 
                  result.errors().get(0), containsString("Step 'step1' output 'invalid' references non-existent target step 'invalid'"));
    }
}