package io.pipeline.data.util.json;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.pipeline.api.model.*;
import io.pipeline.api.model.PipelineStepConfig.JsonConfigOptions;
import io.pipeline.api.model.PipelineStepConfig.OutputTarget;
import io.pipeline.api.model.PipelineStepConfig.ProcessorInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        ProcessorInfo processorInfo1 = new ProcessorInfo(null, "bean-step1");
        ProcessorInfo processorInfo2 = new ProcessorInfo(null, "bean-step2");

        OutputTarget output1 = new OutputTarget("step2", TransportType.INTERNAL, null, null);
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
                new ProcessorInfo(null, "bean-invalid")
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
        ProcessorInfo invalidProcessorInfo = new ProcessorInfo("ab", null);
        
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
        // Use a valid Java identifier for the bean name to avoid errors
        ProcessorInfo processorInfo = new ProcessorInfo(null, "beanStep");
        
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

    private static PipelineStepConfig createStep(String name, StepType type, ProcessorInfo processorInfo,
                                          List<KafkaInputDefinition> kafkaInputs,
                                          Map<String, OutputTarget> outputs) {
        return new PipelineStepConfig(
                name, type, "Test Step " + name, null,
                new JsonConfigOptions(JsonNodeFactory.instance.objectNode(), Collections.emptyMap()),
                kafkaInputs != null ? kafkaInputs : Collections.emptyList(),
                outputs != null ? outputs : Collections.emptyMap(),
                0, 1000L, 30000L, 2.0, null,
                processorInfo
        );
    }
}