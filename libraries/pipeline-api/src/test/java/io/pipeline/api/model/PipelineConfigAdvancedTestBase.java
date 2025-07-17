package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.HashSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Advanced test cases for PipelineConfig covering fan-in/fan-out scenarios
 * and various transport configurations.
 */
public abstract class PipelineConfigAdvancedTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testFanOutConfiguration() throws Exception {
        // Create a pipeline where one step outputs to multiple targets
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // Parser outputs to both chunker and metadata-extractor (fan-out)
        PipelineStepConfig.OutputTarget toChunker = new PipelineStepConfig.OutputTarget(
                "chunker",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("parsed-docs", "pipedocId", "snappy", 32768, 20, Map.of("max.request.size", "20971520"))
        );
        
        PipelineStepConfig.OutputTarget toMetadataExtractor = new PipelineStepConfig.OutputTarget(
                "metadata-extractor",
                TransportType.GRPC,
                new GrpcTransportConfig("metadata-service", Map.of("retry", "3", "timeout", "30000")),
                null
        );
        
        steps.put("parser", new PipelineStepConfig(
                "document-parser",
                StepType.CONNECTOR,
                "Parses documents and fans out to multiple processors",
                null,
                new PipelineStepConfig.JsonConfigOptions(Map.of("format", "pdf")),
                null,
                Map.of("content", toChunker, "metadata", toMetadataExtractor),
                3,
                1000L,
                30000L,
                2.0,
                60000L,
                new PipelineStepConfig.ProcessorInfo("parser-service", null)
        ));
        
        // Add downstream steps
        steps.put("chunker", new PipelineStepConfig(
                "text-chunker",
                StepType.PIPELINE,
                new PipelineStepConfig.ProcessorInfo("chunker-service", null)
        ));
        
        steps.put("metadata-extractor", new PipelineStepConfig(
                "metadata-processor",
                StepType.PIPELINE,
                new PipelineStepConfig.ProcessorInfo(null, "metadataBean")
        ));
        
        PipelineConfig config = new PipelineConfig("fan-out-pipeline", steps);
        
        // Serialize and deserialize
        String json = getObjectMapper().writeValueAsString(config);
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify fan-out configuration
        PipelineStepConfig parserStep = deserialized.pipelineSteps().get("parser");
        assertEquals(2, parserStep.outputs().size());
        assertEquals(Set.of("content", "metadata"), new HashSet<>(parserStep.outputs().keySet()));
        
        // Verify Kafka output
        PipelineStepConfig.OutputTarget contentOutput = parserStep.outputs().get("content");
        assertEquals(TransportType.KAFKA, contentOutput.transportType());
        assertEquals("parsed-docs", contentOutput.kafkaTransport().topic());
        assertEquals("pipedocId", contentOutput.kafkaTransport().partitionKeyField());
        assertEquals("snappy", contentOutput.kafkaTransport().compressionType());
        
        // Verify gRPC output
        PipelineStepConfig.OutputTarget metadataOutput = parserStep.outputs().get("metadata");
        assertEquals(TransportType.GRPC, metadataOutput.transportType());
        assertEquals("metadata-service", metadataOutput.grpcTransport().serviceName());
        assertEquals("3", metadataOutput.grpcTransport().grpcClientProperties().get("retry"));
    }

    @Test
    public void testFanInConfiguration() throws Exception {
        // Create a pipeline where multiple steps feed into one (fan-in)
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // Text extractor outputs to enricher
        PipelineStepConfig.OutputTarget textToEnricher = new PipelineStepConfig.OutputTarget(
                "enricher",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("enrichment-input", null, null, null, null, Map.of("client.id", "text-producer"))
        );
        
        steps.put("text-extractor", new PipelineStepConfig(
                "text-extraction",
                StepType.CONNECTOR,
                "Extracts text from documents",
                null,
                null,
                null,
                Map.of("default", textToEnricher),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("text-service", null)
        ));
        
        // Metadata extractor also outputs to enricher
        PipelineStepConfig.OutputTarget metadataToEnricher = new PipelineStepConfig.OutputTarget(
                "enricher",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("enrichment-input", null, null, null, null, Map.of("client.id", "metadata-producer"))
        );
        
        steps.put("metadata-extractor", new PipelineStepConfig(
                "metadata-extraction",
                StepType.CONNECTOR,
                "Extracts metadata from documents",
                null,
                null,
                null,
                Map.of("default", metadataToEnricher),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("metadata-service", null)
        ));
        
        // Enricher receives from multiple sources (fan-in)
        steps.put("enricher", new PipelineStepConfig(
                "document-enricher",
                StepType.PIPELINE,
                "Enriches documents with data from multiple sources",
                null,
                new PipelineStepConfig.JsonConfigOptions(Map.of("merge-strategy", "combine")),
                List.of(new KafkaInputDefinition(
                    List.of("enrichment-input"),
                    "enricher-group",
                    Map.of("auto.offset.reset", "earliest")
                )),
                null,
                5,
                2000L,
                60000L,
                2.5,
                120000L,
                new PipelineStepConfig.ProcessorInfo("enricher-service", null)
        ));
        
        PipelineConfig config = new PipelineConfig("fan-in-pipeline", steps);
        
        // Serialize and deserialize
        String json = getObjectMapper().writeValueAsString(config);
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify fan-in configuration
        PipelineStepConfig enricherStep = deserialized.pipelineSteps().get("enricher");
        assertEquals(1, enricherStep.kafkaInputs().size());
        assertArrayEquals(new Object[]{"enrichment-input"}, enricherStep.kafkaInputs().get(0).listenTopics().toArray());
        assertEquals("enricher-group", enricherStep.kafkaInputs().get(0).consumerGroupId());
        
        // Verify both extractors point to enricher
        PipelineStepConfig textExtractor = deserialized.pipelineSteps().get("text-extractor");
        assertEquals("enricher", textExtractor.outputs().get("default").targetStepName());
        
        PipelineStepConfig metadataExtractor = deserialized.pipelineSteps().get("metadata-extractor");
        assertEquals("enricher", metadataExtractor.outputs().get("default").targetStepName());
    }

    @Test
    public void testMixedTransportTypes() throws Exception {
        // Test a pipeline with various transport configurations
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // Step with internal transport
        PipelineStepConfig.OutputTarget internalOutput = new PipelineStepConfig.OutputTarget(
                "processor",
                TransportType.INTERNAL,
                null,
                null
        );
        
        steps.put("loader", new PipelineStepConfig(
                "data-loader",
                StepType.CONNECTOR,
                "Loads data using internal transport",
                null,
                null,
                null,
                Map.of("default", internalOutput),
                0,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo(null, "loaderBean")
        ));
        
        // Step with multiple Kafka inputs
        List<KafkaInputDefinition> multipleInputs = List.of(
                new KafkaInputDefinition(
                    List.of("topic1", "topic2"),
                    "consumer-group-1",
                    Map.of("max.poll.records", "500")
                ),
                new KafkaInputDefinition(
                    List.of("topic3"),
                    null, // Let the engine generate the consumer group
                    Map.of("fetch.min.bytes", "1024")
                )
        );
        
        steps.put("aggregator", new PipelineStepConfig(
                "data-aggregator",
                StepType.PIPELINE,
                "Aggregates data from multiple Kafka topics",
                null,
                null,
                multipleInputs,
                null,
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("aggregator-service", null)
        ));
        
        PipelineConfig config = new PipelineConfig("mixed-transport-pipeline", steps);
        
        // Serialize and deserialize
        String json = getObjectMapper().writeValueAsString(config);
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify internal transport
        PipelineStepConfig loaderStep = deserialized.pipelineSteps().get("loader");
        assertEquals(TransportType.INTERNAL, loaderStep.outputs().get("default").transportType());
        assertEquals("loaderBean", loaderStep.processorInfo().internalProcessorBeanName());
        
        // Verify multiple Kafka inputs
        PipelineStepConfig aggregatorStep = deserialized.pipelineSteps().get("aggregator");
        assertEquals(2, aggregatorStep.kafkaInputs().size());
        
        KafkaInputDefinition firstInput = aggregatorStep.kafkaInputs().get(0);
        assertArrayEquals(new Object[]{"topic1", "topic2"}, firstInput.listenTopics().toArray());
        assertEquals("consumer-group-1", firstInput.consumerGroupId());
        
        KafkaInputDefinition secondInput = aggregatorStep.kafkaInputs().get(1);
        assertArrayEquals(new Object[]{"topic3"}, secondInput.listenTopics().toArray());
        assertNull(secondInput.consumerGroupId());
    }

    @Test
    public void testComplexDataFlowPattern() throws Exception {
        // Test a diamond pattern: A -> B,C -> D
        Map<String, PipelineStepConfig> steps = new HashMap<>();
        
        // A outputs to both B and C
        PipelineStepConfig.OutputTarget aToB = new PipelineStepConfig.OutputTarget(
                "step-b",
                TransportType.GRPC,
                new GrpcTransportConfig("service-b", null),
                null
        );
        
        PipelineStepConfig.OutputTarget aToC = new PipelineStepConfig.OutputTarget(
                "step-c",
                TransportType.GRPC,
                new GrpcTransportConfig("service-c", null),
                null
        );
        
        steps.put("step-a", new PipelineStepConfig(
                "initial-processor",
                StepType.CONNECTOR,
                "Initial step that splits processing",
                null,
                null,
                null,
                Map.of("path1", aToB, "path2", aToC),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("service-a", null)
        ));
        
        // B outputs to D
        PipelineStepConfig.OutputTarget bToD = new PipelineStepConfig.OutputTarget(
                "step-d",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("merge-topic", null, null, null, null, null)
        );
        
        steps.put("step-b", new PipelineStepConfig(
                "path1-processor",
                StepType.PIPELINE,
                "Processes path 1",
                null,
                null,
                null,
                Map.of("default", bToD),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("service-b", null)
        ));
        
        // C outputs to D
        PipelineStepConfig.OutputTarget cToD = new PipelineStepConfig.OutputTarget(
                "step-d",
                TransportType.KAFKA,
                null,
                new KafkaTransportConfig("merge-topic", null, null, null, null, null)
        );
        
        steps.put("step-c", new PipelineStepConfig(
                "path2-processor",
                StepType.PIPELINE,
                "Processes path 2",
                null,
                null,
                null,
                Map.of("default", cToD),
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("service-c", null)
        ));
        
        // D receives from both B and C
        steps.put("step-d", new PipelineStepConfig(
                "merge-processor",
                StepType.SINK,
                "Merges results from both paths",
                null,
                null,
                List.of(new KafkaInputDefinition(
                    List.of("merge-topic"),
                    "merge-group",
                    null
                )),
                null,
                3,
                1000L,
                30000L,
                2.0,
                null,
                new PipelineStepConfig.ProcessorInfo("service-d", null)
        ));
        
        PipelineConfig config = new PipelineConfig("diamond-pattern-pipeline", steps);
        
        // Serialize and deserialize
        String json = getObjectMapper().writeValueAsString(config);
        PipelineConfig deserialized = getObjectMapper().readValue(json, PipelineConfig.class);
        
        // Verify the diamond pattern
        assertEquals(4, deserialized.pipelineSteps().size());
        
        // Verify A outputs to both B and C
        PipelineStepConfig stepA = deserialized.pipelineSteps().get("step-a");
        assertEquals(2, stepA.outputs().size());
        assertEquals("step-b", stepA.outputs().get("path1").targetStepName());
        assertEquals("step-c", stepA.outputs().get("path2").targetStepName());
        
        // Verify both B and C output to D
        PipelineStepConfig stepB = deserialized.pipelineSteps().get("step-b");
        assertEquals("step-d", stepB.outputs().get("default").targetStepName());
        
        PipelineStepConfig stepC = deserialized.pipelineSteps().get("step-c");
        assertEquals("step-d", stepC.outputs().get("default").targetStepName());
        
        // Verify D is configured as a sink with Kafka input
        PipelineStepConfig stepD = deserialized.pipelineSteps().get("step-d");
        assertEquals(StepType.SINK, stepD.stepType());
        assertEquals(1, stepD.kafkaInputs().size());
        assertArrayEquals(new Object[]{"merge-topic"}, stepD.kafkaInputs().get(0).listenTopics().toArray());
    }
}
