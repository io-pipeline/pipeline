package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Base test class for PipelineGraphConfig that contains all test logic.
 * Extended by both unit tests and integration tests.
 */
public abstract class PipelineGraphConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testEmptyGraphSerialization() throws Exception {
        PipelineGraphConfig emptyGraph = new PipelineGraphConfig(null);
        
        String json = getObjectMapper().writeValueAsString(emptyGraph);
        assertTrue(json.contains("\"pipelines\":{}"));
        
        // Test round trip
        PipelineGraphConfig deserialized = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        assertTrue(deserialized.pipelines().isEmpty());
    }

    @Test
    public void testSinglePipelineSerialization() throws Exception {
        // Create a simple pipeline
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo("test-service", null);
        PipelineStepConfig step = new PipelineStepConfig("test-step", StepType.SINK, processorInfo);
        
        PipelineConfig pipeline = new PipelineConfig("test-pipeline", Map.of("test-step", step));
        PipelineGraphConfig graph = new PipelineGraphConfig(Map.of("test-pipeline", pipeline));
        
        String json = getObjectMapper().writeValueAsString(graph);
        
        // Verify structure
        assertTrue(json.contains("\"pipelines\""));
        assertTrue(json.contains("\"test-pipeline\""));
        assertTrue(json.contains("\"name\":\"test-pipeline\""));
        assertTrue(json.contains("\"pipelineSteps\""));
        
        // Test round trip
        PipelineGraphConfig deserialized = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        assertEquals(1, deserialized.pipelines().size());
        assertNotNull(deserialized.getPipelineConfig("test-pipeline"));
        assertEquals("test-pipeline", deserialized.getPipelineConfig("test-pipeline").name());
    }

    @Test
    public void testMultiplePipelinesSerialization() throws Exception {
        // Create multiple pipelines
        PipelineStepConfig.ProcessorInfo processor1 = new PipelineStepConfig.ProcessorInfo("service1", null);
        PipelineStepConfig step1 = new PipelineStepConfig("step1", StepType.PIPELINE, processor1);
        PipelineConfig pipeline1 = new PipelineConfig("pipeline1", Map.of("step1", step1));
        
        PipelineStepConfig.ProcessorInfo processor2 = new PipelineStepConfig.ProcessorInfo("service2", null);
        PipelineStepConfig step2 = new PipelineStepConfig("step2", StepType.SINK, processor2);
        PipelineConfig pipeline2 = new PipelineConfig("pipeline2", Map.of("step2", step2));
        
        PipelineGraphConfig graph = new PipelineGraphConfig(Map.of(
            "pipeline1", pipeline1,
            "pipeline2", pipeline2
        ));
        
        String json = getObjectMapper().writeValueAsString(graph);
        PipelineGraphConfig deserialized = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        
        assertEquals(2, deserialized.pipelines().size());
        assertNotNull(deserialized.getPipelineConfig("pipeline1"));
        assertNotNull(deserialized.getPipelineConfig("pipeline2"));
        assertNull(deserialized.getPipelineConfig("nonexistent"));
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "pipelines": {
                    "document-processing": {
                        "name": "document-processing",
                        "pipelineSteps": {
                            "chunker": {
                                "stepName": "chunker",
                                "stepType": "PIPELINE",
                                "processorInfo": {
                                    "grpcServiceName": "chunker-service"
                                }
                            },
                            "embedder": {
                                "stepName": "embedder",
                                "stepType": "SINK",
                                "processorInfo": {
                                    "grpcServiceName": "embedder-service"
                                }
                            }
                        }
                    },
                    "real-time-analysis": {
                        "name": "real-time-analysis",
                        "pipelineSteps": {
                            "analyzer": {
                                "stepName": "analyzer",
                                "stepType": "SINK",
                                "processorInfo": {
                                    "internalProcessorBeanName": "analyzerBean"
                                }
                            }
                        }
                    }
                }
            }
            """;
        
        PipelineGraphConfig graph = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        
        assertEquals(2, graph.pipelines().size());
        
        // Check first pipeline
        PipelineConfig docProcessing = graph.getPipelineConfig("document-processing");
        assertNotNull(docProcessing);
        assertEquals("document-processing", docProcessing.name());
        assertEquals(2, docProcessing.pipelineSteps().size());
        assertEquals(StepType.PIPELINE, docProcessing.pipelineSteps().get("chunker").stepType());
        assertEquals(StepType.SINK, docProcessing.pipelineSteps().get("embedder").stepType());
        
        // Check second pipeline
        PipelineConfig realTimeAnalysis = graph.getPipelineConfig("real-time-analysis");
        assertNotNull(realTimeAnalysis);
        assertEquals(1, realTimeAnalysis.pipelineSteps().size());
        assertEquals("analyzerBean", realTimeAnalysis.pipelineSteps().get("analyzer").processorInfo().internalProcessorBeanName());
    }

    @Test
    public void testImmutability() {
        Map<String, PipelineConfig> mutableMap = new java.util.HashMap<>();
        PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo("service", null);
        PipelineStepConfig step = new PipelineStepConfig("step", StepType.SINK, processor);
        PipelineConfig pipeline = new PipelineConfig("pipeline", Map.of("step", step));
        mutableMap.put("pipeline", pipeline);
        
        PipelineGraphConfig graph = new PipelineGraphConfig(mutableMap);
        
        // Original map modification should not affect the graph
        mutableMap.put("another", pipeline);
        assertEquals(1, graph.pipelines().size());
        
        // Returned map should be immutable
        assertThrows(UnsupportedOperationException.class, () -> 
            graph.pipelines().put("new", pipeline)
        );
    }

    @Test
    public void testNullHandling() throws Exception {
        // Test with null map
        PipelineGraphConfig graphWithNull = new PipelineGraphConfig(null);
        assertTrue(graphWithNull.pipelines().isEmpty());
        
        String json = getObjectMapper().writeValueAsString(graphWithNull);
        PipelineGraphConfig deserialized = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        assertTrue(deserialized.pipelines().isEmpty());
    }

    @Test
    public void testComplexGraphRoundTrip() throws Exception {
        // Create a complex graph with multiple pipelines and steps
        PipelineGraphConfig originalGraph = createComplexGraph();
        
        // Serialize to JSON
        String json = getObjectMapper().writeValueAsString(originalGraph);
        
        // Deserialize back
        PipelineGraphConfig deserializedGraph = getObjectMapper().readValue(json, PipelineGraphConfig.class);
        
        // Verify the graphs are equivalent
        assertEquals(originalGraph.pipelines().size(), deserializedGraph.pipelines().size());
        
        for (String pipelineName : originalGraph.pipelines().keySet()) {
            PipelineConfig original = originalGraph.getPipelineConfig(pipelineName);
            PipelineConfig deserialized = deserializedGraph.getPipelineConfig(pipelineName);
            
            assertNotNull(deserialized);
            assertEquals(original.name(), deserialized.name());
            assertEquals(original.pipelineSteps().size(), deserialized.pipelineSteps().size());
        }
    }
    
    private PipelineGraphConfig createComplexGraph() {
        // Pipeline 1: Document processing with chunking and embedding
        PipelineStepConfig.ProcessorInfo chunkerProcessor = new PipelineStepConfig.ProcessorInfo("chunker-service", null);
        PipelineStepConfig.ProcessorInfo embedderProcessor = new PipelineStepConfig.ProcessorInfo("embedder-service", null);
        
        GrpcTransportConfig grpcTransport = new GrpcTransportConfig("embedder-service", Map.of("timeout", "30s"));
        PipelineStepConfig.OutputTarget chunkerOutput = new PipelineStepConfig.OutputTarget(
            "embedder", TransportType.GRPC, grpcTransport, null);
        
        PipelineStepConfig chunkerStep = new PipelineStepConfig(
            "chunker", StepType.INITIAL_PIPELINE, "Chunks documents",
            null, null, Collections.emptyList(), Map.of("output", chunkerOutput),
            3, 1000L, 30000L, 2.0, null, chunkerProcessor);
            
        PipelineStepConfig embedderStep = new PipelineStepConfig(
            "embedder", StepType.SINK, "Creates embeddings",
            null, null, Collections.emptyList(), Collections.emptyMap(),
            3, 1000L, 30000L, 2.0, null, embedderProcessor);
            
        PipelineConfig docProcessing = new PipelineConfig("document-processing", Map.of(
            "chunker", chunkerStep,
            "embedder", embedderStep
        ));
        
        // Pipeline 2: Real-time analysis
        PipelineStepConfig.ProcessorInfo analyzerProcessor = new PipelineStepConfig.ProcessorInfo(null, "analyzerBean");
        PipelineStepConfig analyzerStep = new PipelineStepConfig(
            "analyzer", StepType.SINK, analyzerProcessor);
        PipelineConfig realTimeAnalysis = new PipelineConfig("real-time-analysis", Map.of(
            "analyzer", analyzerStep
        ));
        
        return new PipelineGraphConfig(Map.of(
            "document-processing", docProcessing,
            "real-time-analysis", realTimeAnalysis
        ));
    }
}