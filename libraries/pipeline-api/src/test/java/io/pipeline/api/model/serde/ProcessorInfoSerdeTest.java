
package io.pipeline.api.model.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipeline.api.model.PipelineStepConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ProcessorInfoSerdeTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testProcessorInfoSerde() throws Exception {
        // 1. Create the Java object
        var original = new PipelineStepConfig.ProcessorInfo(null, "my-internal-bean");

        // 2. Serialize it to JSON
        String json = objectMapper.writeValueAsString(original);

        // 3. Deserialize it back to a Java object
        var deserialized = objectMapper.readValue(json, PipelineStepConfig.ProcessorInfo.class);

        // 4. Verify they are equal
        assertEquals(original, deserialized);

        // Also print the correct JSON to the console for verification
        System.out.println("--- Canonical ProcessorInfo JSON ---");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original));
        System.out.println("--- End Canonical JSON ---");
    }
}
