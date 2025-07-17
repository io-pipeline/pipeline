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
                Collections.emptySet()   // allowedGrpcServices
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
                null, // null name is invalid according to schema
                StepType.CONNECTOR,
                "Invalid Step",
                null,
                new JsonConfigOptions(JsonNodeFactory.instance.objectNode(), Collections.emptyMap()),
                Collections.emptyList(),
                Collections.emptyMap(),
                0, 1000L, 30000L, 2.0, null,
                new ProcessorInfo(null, "bean-invalid")
        );
        
        return new PipelineConfig("invalid-pipeline-with-schema-violation", Map.of(
                "invalid-step", invalidStep
        ));
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
