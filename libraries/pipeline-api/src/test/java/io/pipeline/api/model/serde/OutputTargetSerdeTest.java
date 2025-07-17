package io.pipeline.api.model.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipeline.api.model.GrpcTransportConfig;
import io.pipeline.api.model.KafkaTransportConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.TransportType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class OutputTargetSerdeTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testKafkaOutputTargetSerde() throws Exception {
        // 1. Create the Java object
        var kafkaTransport = new KafkaTransportConfig("my-output-topic", "pipedocId", "gzip", 1024, 5, Map.of("acks", "all"));
        var original = new PipelineStepConfig.OutputTarget("the-next-step", TransportType.KAFKA, null, kafkaTransport);

        // 2. Serialize it to JSON
        String json = objectMapper.writeValueAsString(original);

        // 3. Deserialize it back to a Java object
        var deserialized = objectMapper.readValue(json, PipelineStepConfig.OutputTarget.class);

        // 4. Verify they are equal
        assertEquals(original, deserialized);

        // Also print the correct JSON to the console for verification
        System.out.println("--- Canonical Kafka OutputTarget JSON ---");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original));
        System.out.println("--- End Canonical JSON ---");
    }

    @Test
    void testGrpcOutputTargetSerde() throws Exception {
        // 1. Create the Java object
        var grpcTransport = new GrpcTransportConfig("my-grpc-service", Map.of("timeout", "5000"));
        var original = new PipelineStepConfig.OutputTarget("the-next-step", TransportType.GRPC, grpcTransport, null);

        // 2. Serialize it to JSON
        String json = objectMapper.writeValueAsString(original);

        // 3. Deserialize it back to a Java object
        var deserialized = objectMapper.readValue(json, PipelineStepConfig.OutputTarget.class);

        // 4. Verify they are equal
        assertEquals(original, deserialized);

        // Also print the correct JSON to the console for verification
        System.out.println("--- Canonical gRPC OutputTarget JSON ---");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original));
        System.out.println("--- End Canonical JSON ---");
    }
}