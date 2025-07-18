
package io.pipeline.api.model.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipeline.api.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PipelineConfigSerdeTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testPipelineConfigSerde() throws Exception {
        // 1. Create the constituent parts
        var kafkaInput = new KafkaInputDefinition(List.of("topic-a"), "my-consumer-group", null);
        var kafkaTransport = new KafkaTransportConfig("my-output-topic", "pipedocId", null, null, null, null);
        var outputTarget = new PipelineStepConfig.OutputTarget("next-step", TransportType.KAFKA, null, kafkaTransport);
        var processorInfo = new PipelineStepConfig.ProcessorInfo("my-processor");
        var step = new PipelineStepConfig(
                "my-step",
                StepType.PIPELINE,
                "A test step",
                null,
                null,
                List.of(kafkaInput),
                Map.of("default", outputTarget),
                10, 1000L, 30000L, 2.0, 60000L,
                processorInfo
        );

        // 2. Create the PipelineConfig object
        var original = new PipelineConfig("my-pipeline", Map.of("my-step", step));

        // 3. Serialize it to JSON
        String json = objectMapper.writeValueAsString(original);

        // 4. Deserialize it back to a Java object
        var deserialized = objectMapper.readValue(json, PipelineConfig.class);

        // 5. Verify they are equal
        assertEquals(original, deserialized);

        // Also print the correct JSON to the console for verification
        System.out.println("--- Canonical PipelineConfig JSON ---");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original));
        System.out.println("--- End Canonical JSON ---");
    }
}
