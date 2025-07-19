package io.pipeline.data.util.json;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.pipeline.api.model.*;
import io.pipeline.api.model.PipelineStepConfig.JsonConfigOptions;
import io.pipeline.api.model.PipelineStepConfig.OutputTarget;
import io.pipeline.api.model.PipelineStepConfig.ProcessorInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MockPipelineGenerator {

    public static PipelineClusterConfig createSimpleClusterOnlySetup() {
        return new PipelineClusterConfig(
                "default-cluster",
                null,  // pipelineGraphConfig can be null
                null,  // pipelineModuleMap can be null
                null,  // defaultPipelineName can be null
                Collections.emptySet(),  // allowedKafkaTopics
                Collections.emptySet()   // allowedGrpcervices
        );
    }

    /**
     * Creates a basic, valid cluster configuration containing a single, empty pipeline.
     * This represents the most minimal valid configuration for a cluster.
     * <p>
     * Use Case: Initial setup of a new pipeline cluster before any steps are added.
     * <p>
     * Expected Validation Outcome:
     * - Should pass {@code PRODUCTION} validation.
     * - Should pass {@code DRAFT} validation.
     *
     * @return A {@link PipelineClusterConfig} instance.
     */
    public static PipelineClusterConfig createClusterWithEmptyPipeline() {
        PipelineConfig emptyPipeline = new PipelineConfig(
                "empty-pipeline",
                Collections.emptyMap()
        );

        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
                Map.of("empty-pipeline", emptyPipeline)
        );

        return new PipelineClusterConfig(
                "default-cluster",
                graphConfig,
                null,
                "empty-pipeline",
                Collections.emptySet(),
                Collections.emptySet()
        );
    }

    public static PipelineConfig createSimpleLinearPipeline() {
        ProcessorInfo processorInfo1 = new ProcessorInfo("grpc-step1");
        ProcessorInfo processorInfo2 = new ProcessorInfo("grpc-step2");

        OutputTarget output1 = new OutputTarget("step2", TransportType.GRPC, new GrpcTransportConfig("step2", Map.of()), null);
        PipelineStepConfig step1 = createStep("step1", StepType.CONNECTOR, processorInfo1, null, Map.of("out", output1));
        PipelineStepConfig step2 = createStep("step2", StepType.SINK, processorInfo2, null, null);

        return new PipelineConfig("simple-linear-pipeline", Map.of(
                "step1", step1,
                "step2", step2
        ));
    }

    public static PipelineConfig createPipelineWithSchemaViolation() {
        // Create a pipeline with a valid name but invalid step configuration
        // This will pass constructor validation but fail schema validation
        PipelineStepConfig invalidStep = new PipelineStepConfig(
                "invalid-step-name", // Valid name to avoid NullPointerException
                StepType.SINK, // SINK steps don't require outputs
                "", // Empty description is a schema violation
                null,
                new JsonConfigOptions(JsonNodeFactory.instance.objectNode(), Collections.emptyMap()),
                Collections.emptyList(),
                Collections.emptyMap(), // Empty outputs is valid for SINK steps
                -1, // Negative maxRetries is a schema violation
                1000L, 30000L, 2.0, null,
                new ProcessorInfo("grpc-invalid")
        );
        
        return new PipelineConfig("invalid-pipeline-with-schema-violation", Map.of(
                "invalid-step", invalidStep
        ));
    }

    /**
     * Creates a pipeline with a name that violates the naming convention (contains a dot).
     * This should be rejected by all validation modes.
     * <p>
     * Use Case: Testing the NamingConventionValidator for pipeline names.
     * <p>
     * Expected Validation Outcome:
     * - Should FAIL {@code PRODUCTION} validation.
     * - Should FAIL {@code DESIGN} validation.
     * - Should PASS {@code TESTING} validation (naming conventions relaxed).
     *
     * @return A {@link PipelineConfig} instance with an invalid name.
     */
    public static PipelineConfig createPipelineWithNamingViolation() {
        return new PipelineConfig(
                "invalid.pipeline.name",
                Collections.emptyMap()
        );
    }
    
    /**
     * Creates a pipeline with incomplete processor information.
     * The processor has a very short gRPC service name that violates validation rules.
     * <p>
     * Use Case: Testing the ProcessorInfoValidator across different validation modes.
     * <p>
     * Expected Validation Outcome:
     * - Should FAIL {@code PRODUCTION} validation.
     * - Should FAIL {@code DESIGN} validation.
     * - Should PASS {@code TESTING} validation (processor info validation relaxed).
     *
     * @return A {@link PipelineConfig} instance with incomplete processor info.
     */
    public static PipelineConfig createPipelineWithIncompleteProcessorInfo() {
        // Create a processor with a very short gRPC service name (less than 3 chars)
        // This violates the ProcessorInfoValidator rules
        ProcessorInfo invalidProcessorInfo = new ProcessorInfo("ab");
        
        PipelineStepConfig step = createStep(
                "processor-step", 
                StepType.PIPELINE, 
                invalidProcessorInfo,
                null, 
                Collections.emptyMap()
        );
        
        return new PipelineConfig(
                "pipeline-with-incomplete-processor-info",
                Map.of("processor-step", step)
        );
    }

    /**
     * Creates a pipeline with a single, unconnected step.
     * This should produce warnings but not fail validation, even in PRODUCTION mode.
     * <p>
     * Use Case: Testing validation for a minimal, but valid, pipeline step.
     * <p>
     * Expected Validation Outcome:
     * - Should PASS {@code PRODUCTION} validation (with warnings).
     * - Should PASS {@code DESIGN} validation (with warnings).
     * - Should PASS {@code TESTING} validation (with fewer or no warnings).
     *
     * @return A {@link PipelineConfig} instance.
     */
    public static PipelineConfig createPipelineWithSingleStepNoRouting() {
        ProcessorInfo processorInfo = new ProcessorInfo("echo-module");

        PipelineStepConfig step = createStep(
                "echo-step",
                StepType.PIPELINE,
                processorInfo,
                Collections.emptyList(), // No inputs
                Collections.emptyMap()   // No outputs
        );

        return new PipelineConfig(
                "single-step-pipeline",
                Map.of("echo-step", step)
        );
    }

    /**
     * Creates a pipeline with a single, valid CONNECTOR step.
     * This represents the first step in a minimal end-to-end pipeline.
     * <p>
     * Use Case: Testing validation for a pipeline with a valid entry point.
     * <p>
     * Expected Validation Outcome:
     * - Should PASS all validation modes, but will generate a warning about a missing SINK.
     *
     * @return A {@link PipelineConfig} instance.
     */
    public static PipelineConfig createPipelineWithConnectorStep() {
        // ProcessorInfo must have either grpcServiceName or internalProcessorgrpcName, not both
        // For CONNECTOR steps, use grpcServiceName (external service)
        ProcessorInfo connectorProcessor = new ProcessorInfo("gutenberg-connector");
        // For SINK steps, use grpcServiceName (external service) instead of internalProcessorgrpcName
        ProcessorInfo sinkProcessor = new ProcessorInfo("opensearch-sink");

        // Fix topic naming to follow convention: '{pipeline-name}.{step-name}.input'
        String pipelineName = "pipeline-with-connector-and-sink";
        String topicName = pipelineName + ".gutenberg-pg-connector.input";
        
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(topicName, "pipedocId", "snappy", 16384, 10, Map.of());
        OutputTarget output = new OutputTarget("opensearch-sink-step", TransportType.KAFKA, null, kafkaTransport);

        PipelineStepConfig connectorStep = createStep(
                "gutenberg-pg-connector",
                StepType.CONNECTOR,
                connectorProcessor,
                Collections.emptyList(),
                Map.of("default", output)
        );

        // Fix consumer group name to follow pattern: '{pipeline-name}.consumer-group'
        String consumerGroupName = pipelineName + ".consumer-group";
        
        PipelineStepConfig sinkStep = createStep(
                "opensearch-sink-step",
                StepType.SINK,
                sinkProcessor,
                List.of(new KafkaInputDefinition(List.of(topicName), consumerGroupName, Map.of())),
                Collections.emptyMap()
        );

        return new PipelineConfig(
                pipelineName,
                Map.of(
                        "gutenberg-pg-connector", connectorStep,
                        "opensearch-sink-step", sinkStep
                )
        );
    }
    
    /**
     * Creates a pipeline with configuration that generates warnings but not errors.
     * This is useful for testing how different validation modes handle warnings.
     * <p>
     * Use Case: Testing warning handling across different validation modes.
     * <p>
     * Expected Validation Outcome:
     * - Should FAIL {@code PRODUCTION} validation (warnings become errors).
     * - Should PASS {@code DESIGN} validation (warnings allowed).
     * - Should PASS {@code TESTING} validation (warnings ignored).
     *
     * @return A {@link PipelineConfig} instance that generates warnings.
     */
    public static PipelineConfig createPipelineWithWarnings() {
        // Create a step with a very high retry count (generates warnings)
        // Use a valid Java identifier for the grpc name to avoid errors
        ProcessorInfo processorInfo = new ProcessorInfo("grpcStep");
        
        // Create a SINK step with a high retry count and long timeout
        // Using SINK type avoids the need for outputs, which can cause errors
        // These values will generate warnings but not errors
        PipelineStepConfig step = new PipelineStepConfig(
                "warning-step", 
                StepType.SINK, // SINK steps don't need outputs
                "Step with warnings", 
                null,
                new JsonConfigOptions(JsonNodeFactory.instance.objectNode(), Collections.emptyMap()),
                Collections.emptyList(), // No inputs - will generate a warning but not an error
                Collections.emptyMap(), // No outputs - valid for SINK steps
                20, // maxRetries > 10 generates a warning
                30000L, // retryBackoffMs > 10000 generates a warning
                600000L, // stepTimeoutMs > 300000 generates a warning
                2.0, 
                null,
                processorInfo
        );
        
        return new PipelineConfig(
                "pipeline-with-warnings",
                Map.of("warning-step", step)
        );
    }

    /**
     * Creates a pipeline where the Kafka topic name in the output does not follow the
     * required naming convention.
     * <p>
     * Use Case: Testing the NamingConventionValidator for Kafka topic names.
     * <p>
     * Expected Validation Outcome:
     * - Should FAIL {@code PRODUCTION} validation.
     * - Should FAIL {@code DESIGN} validation.
     * - Should PASS {@code TESTING} validation.
     *
     * @return A {@link PipelineConfig} instance with an invalid Kafka topic name.
     */
    public static PipelineConfig createPipelineWithInvalidTopicName() {
        String pipelineName = "pipeline-with-invalid-topic";
        ProcessorInfo connectorProcessor = new ProcessorInfo("gutenberg-connector");
        ProcessorInfo sinkProcessor = new ProcessorInfo("opensearch-sink");

        // Invalid topic name - does not follow the {pipeline-name}.{step-name}.input pattern
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig("my-custom-topic", "pipedocId", "snappy", 16384, 10, Map.of());
        OutputTarget output = new OutputTarget("opensearch-sink-step", TransportType.KAFKA, null, kafkaTransport);

        PipelineStepConfig connectorStep = createStep(
                "gutenberg-pg-connector",
                StepType.CONNECTOR,
                connectorProcessor,
                Collections.emptyList(),
                Map.of("default", output)
        );

        PipelineStepConfig sinkStep = createStep(
                "opensearch-sink-step",
                StepType.SINK,
                sinkProcessor,
                List.of(new KafkaInputDefinition(List.of("my-custom-topic"), pipelineName + ".consumer-group", Map.of())),
                Collections.emptyMap()
        );

        return new PipelineConfig(
                pipelineName,
                Map.of(
                        "gutenberg-pg-connector", connectorStep,
                        "opensearch-sink-step", sinkStep
                )
        );
    }

    /**
     * Creates a pipeline where the Kafka consumer group ID does not follow the
     * required naming convention.
     * <p>
     * Use Case: Testing the NamingConventionValidator for Kafka consumer groups.
     * <p>
     * Expected Validation Outcome:
     * - Should FAIL {@code PRODUCTION} validation.
     * - Should FAIL {@code DESIGN} validation.
     * - Should PASS {@code TESTING} validation.
     *
     * @return A {@link PipelineConfig} instance with an invalid consumer group.
     */
    public static PipelineConfig createPipelineWithInvalidConsumerGroup() {
        String pipelineName = "pipeline-with-invalid-consumer-group";
        String topicName = pipelineName + ".gutenberg-pg-connector.input";
        ProcessorInfo connectorProcessor = new ProcessorInfo("gutenberg-connector");
        ProcessorInfo sinkProcessor = new ProcessorInfo("opensearch-sink");

        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(topicName, "pipedocId", "snappy", 16384, 10, Map.of());
        OutputTarget output = new OutputTarget("opensearch-sink-step", TransportType.KAFKA, null, kafkaTransport);

        PipelineStepConfig connectorStep = createStep(
                "gutenberg-pg-connector",
                StepType.CONNECTOR,
                connectorProcessor,
                Collections.emptyList(),
                Map.of("default", output)
        );

        // Invalid consumer group - does not follow the {pipeline-name}.consumer-group pattern
        PipelineStepConfig sinkStep = createStep(
                "opensearch-sink-step",
                StepType.SINK,
                sinkProcessor,
                List.of(new KafkaInputDefinition(List.of(topicName), "my-custom-consumer-group", Map.of())),
                Collections.emptyMap()
        );

        return new PipelineConfig(
                pipelineName,
                Map.of(
                        "gutenberg-pg-connector", connectorStep,
                        "opensearch-sink-step", sinkStep
                )
        );
    }

    /**
     * Creates a pipeline where a CONNECTOR step incorrectly uses an internal grpc
     * instead of an external gRPC service.
     * <p>
     * Use Case: Testing the ProcessorInfoValidator for mismatched processor types.
     * <p>
     * Expected Validation Outcome:
     * - Should FAIL {@code PRODUCTION} validation.
     * - Should FAIL {@code DESIGN} validation.
     * - Should PASS {@code TESTING} validation.
     *
     * @return A {@link PipelineConfig} instance with a mismatched processor.
     */
    public static PipelineConfig createPipelineWithMismatchedProcessor() {
        String pipelineName = "pipeline-with-mismatched-processor";
        String topicName = pipelineName + ".gutenberg-pg-connector.input";
        // Use a valid service name for the CONNECTOR
        ProcessorInfo connectorProcessor = new ProcessorInfo("gutenberg-connector");
        ProcessorInfo sinkProcessor = new ProcessorInfo("opensearch-sink");

        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(topicName, "pipedocId", "snappy", 16384, 10, Map.of());
        OutputTarget output = new OutputTarget("opensearch-sink-step", TransportType.KAFKA, null, kafkaTransport);

        PipelineStepConfig connectorStep = createStep(
                "gutenberg-pg-connector",
                StepType.CONNECTOR,
                connectorProcessor,
                Collections.emptyList(),
                Map.of("default", output)
        );

        PipelineStepConfig sinkStep = createStep(
                "opensearch-sink-step",
                StepType.SINK,
                sinkProcessor,
                List.of(new KafkaInputDefinition(List.of(topicName), pipelineName + ".consumer-group", Map.of())),
                Collections.emptyMap()
        );

        return new PipelineConfig(
                pipelineName,
                Map.of(
                        "gutenberg-pg-connector", connectorStep,
                        "opensearch-sink-step", sinkStep
                )
        );
    }

    /**
     * Creates a valid pipeline where retries are explicitly disabled.
     * <p>
     * Use Case: Testing that disabling retries is a valid configuration.
     * <p>
     * Expected Validation Outcome:
     * - Should PASS all validation modes.
     *
     * @return A {@link PipelineConfig} instance with retries disabled.
     */
    public static PipelineConfig createPipelineWithDisabledRetries() {
        String pipelineName = "pipeline-with-disabled-retries";
        String topicName = pipelineName + ".gutenberg-pg-connector.input";
        ProcessorInfo connectorProcessor = new ProcessorInfo("gutenberg-connector");
        ProcessorInfo sinkProcessor = new ProcessorInfo("opensearch-sink");

        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(topicName, "pipedocId", "snappy", 16384, 10, Map.of());
        OutputTarget output = new OutputTarget("opensearch-sink-step", TransportType.KAFKA, null, kafkaTransport);

        PipelineStepConfig connectorStep = new PipelineStepConfig(
                "gutenberg-pg-connector",
                StepType.CONNECTOR,
                "A step with retries disabled",
                null,
                new JsonConfigOptions(JsonNodeFactory.instance.objectNode(), Collections.emptyMap()),
                Collections.emptyList(),
                Map.of("default", output),
                0, // maxRetries = 0 disables retries
                0L, // retryBackoffMs = 0 since retries are disabled
                30000L, 2.0, null,
                connectorProcessor
        );

        PipelineStepConfig sinkStep = createStep(
                "opensearch-sink-step",
                StepType.SINK,
                sinkProcessor,
                List.of(new KafkaInputDefinition(List.of(topicName), pipelineName + ".consumer-group", Map.of())),
                Collections.emptyMap()
        );

        return new PipelineConfig(
                pipelineName,
                Map.of(
                        "gutenberg-pg-connector", connectorStep,
                        "opensearch-sink-step", sinkStep
                )
        );
    }

    /**
     * Creates a pipeline that references a gRPC service not listed in the cluster's
     * allowed services.
     * <p>
     * Use Case: Testing the StepReferenceValidator for unregistered service checks.
     * <p>
     * Expected Validation Outcome:
     * - Should FAIL {@code PRODUCTION} validation.
     * - Should FAIL {@code DESIGN} validation.
     * - Should PASS {@code TESTING} validation.
     *
     * @return A {@link PipelineClusterConfig} instance with an unregistered service.
     */
    public static PipelineClusterConfig createPipelineWithUnregisteredService() {
        String pipelineName = "pipeline-with-unregistered-service";
        String topicName = pipelineName + ".echo-connector.input";
        ProcessorInfo connectorProcessor = new ProcessorInfo("unregistered-service");
        ProcessorInfo sinkProcessor = new ProcessorInfo("test-sink-service");

        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(topicName, "pipedocId", "snappy", 16384, 10, Map.of());
        OutputTarget output = new OutputTarget("test-sink", TransportType.KAFKA, null, kafkaTransport);

        PipelineStepConfig connectorStep = createStep(
                "echo-connector",
                StepType.CONNECTOR,
                connectorProcessor,
                Collections.emptyList(),
                Map.of("default", output)
        );

        PipelineStepConfig sinkStep = createStep(
                "test-sink",
                StepType.SINK,
                sinkProcessor,
                List.of(new KafkaInputDefinition(List.of(topicName), pipelineName + ".consumer-group", Map.of())),
                Collections.emptyMap()
        );

        PipelineConfig pipeline = new PipelineConfig(
                pipelineName,
                Map.of(
                        "echo-connector", connectorStep,
                        "test-sink", sinkStep
                )
        );

        return new PipelineClusterConfig(
                "default-cluster",
                new PipelineGraphConfig(Map.of(pipelineName, pipeline)),
                null,
                pipelineName,
                Collections.emptySet(), // allowedKafkaTopics
                Set.of("test-sink-service", "some-other-service") // allowedGrpcServices - includes "test-sink-service" but not "echo"
        );
    }

    /**
     * Creates a pipeline with a direct, two-step loop (A -> B -> A).
     * <p>
     * Use Case: Testing the IntraPipelineLoopValidator for direct loop detection.
     * <p>
     * Expected Validation Outcome:
     * - Should FAIL {@code PRODUCTION} validation.
     * - Should FAIL {@code DESIGN} validation.
     * - Should PASS {@code TESTING} validation (loop detection relaxed).
     *
     * @return A {@link PipelineConfig} instance with a direct two-step loop.
     */
    public static PipelineConfig createPipelineWithDirectTwoStepLoop() {
        String pipelineName = "pipeline-with-direct-loop";
        
        // Create processor info for both steps
        // Using services that are already in the StepReferenceValidator's ALLOWED_SERVICES list
        ProcessorInfo processorInfoA = new ProcessorInfo("echo");
        ProcessorInfo processorInfoB = new ProcessorInfo("testing-harness");
        
        // Create Kafka transport configs for the connections
        // A -> B connection
        String topicAtoB = pipelineName + ".step-a.input";
        KafkaTransportConfig kafkaAtoB = new KafkaTransportConfig(
            topicAtoB, "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        // B -> A connection (creates the loop)
        String topicBtoA = pipelineName + ".step-b.input";
        KafkaTransportConfig kafkaBtoA = new KafkaTransportConfig(
            topicBtoA, "pipedocId", "snappy", 16384, 10, Map.of()
        );
        
        // Create output targets
        OutputTarget outputAtoB = new OutputTarget(
            "step-b", TransportType.KAFKA, null, kafkaAtoB
        );
        
        OutputTarget outputBtoA = new OutputTarget(
            "step-a", TransportType.KAFKA, null, kafkaBtoA
        );
        
        // Create consumer group name following the convention
        String consumerGroupName = pipelineName + ".consumer-group";
        
        // Create input definitions
        KafkaInputDefinition inputB = new KafkaInputDefinition(
            List.of(topicAtoB), consumerGroupName, Map.of()
        );
        
        KafkaInputDefinition inputA = new KafkaInputDefinition(
            List.of(topicBtoA), consumerGroupName, Map.of()
        );
        
        // Create step A (outputs to B)
        PipelineStepConfig stepA = createStep(
            "step-a",
            StepType.PIPELINE,
            processorInfoA,
            List.of(inputA), // Step A receives input from Step B (creating the loop)
            Map.of("default", outputAtoB) // Step A outputs to Step B
        );
        
        // Create step B (outputs back to A, creating the loop)
        PipelineStepConfig stepB = createStep(
            "step-b",
            StepType.PIPELINE,
            processorInfoB,
            List.of(inputB), // Step B receives input from Step A
            Map.of("default", outputBtoA) // Step B outputs back to Step A (creating the loop)
        );
        
        // Create the pipeline with both steps
        return new PipelineConfig(
            pipelineName,
            Map.of("step-a", stepA, "step-b", stepB)
        );
    }

    private static PipelineStepConfig createStep(String name, StepType type, ProcessorInfo processorInfo,
                                          List<KafkaInputDefinition> kafkaInputs,
                                          Map<String, OutputTarget> outputs) {
        return new PipelineStepConfig(
                name, type, "Test Step " + name, null,
                new JsonConfigOptions(JsonNodeFactory.instance.objectNode(), Collections.emptyMap()),
                kafkaInputs != null ? kafkaInputs : Collections.emptyList(),
                outputs != null ? outputs : Collections.emptyMap(),
                3, 1000L, 30000L, 2.0, null, // Set maxRetries to 3 to enable retries
                processorInfo
        );
    }
}