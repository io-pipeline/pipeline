
package io.pipeline.api.model.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipeline.api.model.KafkaInputDefinition;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class KafkaInputDefinitionSerdeTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testKafkaInputDefinitionSerde() throws Exception {
        // 1. Create the Java object
        var original = new KafkaInputDefinition(
                List.of("topic-a", "topic-b"),
                "my-consumer-group",
                Map.of("max.poll.records", "500")
        );

        // 2. Serialize it to JSON
        String json = objectMapper.writeValueAsString(original);

        // 3. Deserialize it back to a Java object
        var deserialized = objectMapper.readValue(json, KafkaInputDefinition.class);

        // 4. Verify they are equal
        assertEquals(original, deserialized);

        // Also print the correct JSON to the console for verification
        //TODO: use proper jboss logging
//        System.out.println("--- Canonical KafkaInputDefinition JSON ---");
//        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original));
//        System.out.println("--- End Canonical JSON ---");
    }
}
