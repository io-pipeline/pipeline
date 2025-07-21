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

/**
 * Generates test pipeline configurations using REAL working services.
 * Unlike MockPipelineGenerator which creates example/mock data, this creates
 * configurations that reference actual registered services like chunker, echo, etc.
 * 
 * These configurations are designed to pass production validation and work
 * with the actual pipeline engine for end-to-end testing.
 */
public class TestPipelineGenerator {

    /**
     * Creates a simple pipeline that processes documents through chunker to embedder.
     * This represents a realistic document processing flow using working services.
     * <p>
     * Flow: ConnectorEngine → chunker (PIPELINE) → embedder (SINK)
     * <p>
     * Expected Validation Outcome:
     * - Should PASS PRODUCTION validation (references real services)
     * - chunker and embedder must be registered and whitelisted
     *
     * @return A {@link PipelineConfig} instance for filesystem processing
     */
    public static PipelineConfig createFilesystemPipeline() {
        String pipelineName = "filesystem-pipeline";
        
        // Step 1: Chunker step (PIPELINE type - processes documents)
        ProcessorInfo chunkerProcessor = new ProcessorInfo("chunker");
        
        // Chunker outputs to embedder via gRPC (direct service call)
        OutputTarget chunkerOutput = new OutputTarget(
            "embedder-sink", 
            TransportType.GRPC, 
            new GrpcTransportConfig("embedder", Map.of()), 
            null
        );
        
        PipelineStepConfig chunkerStep = createStep(
            "chunker-step",
            StepType.PIPELINE,
            chunkerProcessor,
            Collections.emptyList(), // No Kafka inputs - receives directly from ConnectorEngine
            Map.of("default", chunkerOutput)
        );
        
        // Step 2: Embedder sink (SINK type - final destination)
        ProcessorInfo embedderProcessor = new ProcessorInfo("embedder");
        
        PipelineStepConfig embedderStep = createStep(
            "embedder-sink",
            StepType.SINK,
            embedderProcessor,
            Collections.emptyList(), // No Kafka inputs - receives via gRPC from chunker
            Collections.emptyMap() // SINK steps don't have outputs
        );
        
        return new PipelineConfig(
            pipelineName,
            Map.of(
                "chunker-step", chunkerStep,
                "embedder-sink", embedderStep
            )
        );
    }
    
    /**
     * Creates a simple single-step pipeline using only chunker.
     * Useful for testing basic routing without complex processing.
     * <p>
     * Flow: ConnectorEngine → chunker (SINK)
     * <p>
     * Expected Validation Outcome:
     * - Should PASS all validation modes
     * - chunker must be registered and whitelisted
     *
     * @return A {@link PipelineConfig} instance for simple chunker testing
     */
    public static PipelineConfig createEchoPipeline() {
        String pipelineName = "echo-pipeline";
        
        ProcessorInfo chunkerProcessor = new ProcessorInfo("chunker");
        
        PipelineStepConfig chunkerStep = createStep(
            "chunker-step",
            StepType.SINK,
            chunkerProcessor,
            Collections.emptyList(), // No Kafka inputs - receives directly from ConnectorEngine
            Collections.emptyMap() // SINK steps don't have outputs
        );
        
        return new PipelineConfig(
            pipelineName,
            Map.of("chunker-step", chunkerStep)
        );
    }
    
    /**
     * Creates the default pipeline configuration.
     * This matches what our connector mapping logic expects for unknown connector types.
     * <p>
     * Expected Validation Outcome:
     * - Should PASS all validation modes
     * - Uses embedder as a safe default processing destination
     *
     * @return A {@link PipelineConfig} instance for default processing
     */
    public static PipelineConfig createDefaultPipeline() {
        String pipelineName = "default-pipeline";
        
        ProcessorInfo embedderProcessor = new ProcessorInfo("embedder");
        
        PipelineStepConfig embedderStep = createStep(
            "default-step",
            StepType.SINK,
            embedderProcessor,
            Collections.emptyList(),
            Collections.emptyMap()
        );
        
        return new PipelineConfig(
            pipelineName,
            Map.of("default-step", embedderStep)
        );
    }
    
    /**
     * Creates a complete cluster configuration with multiple test pipelines.
     * This includes proper whitelisting of services for validation.
     * <p>
     * Contains:
     * - filesystem-pipeline (chunker → echo)
     * - echo-pipeline (simple echo)
     * - default-pipeline (fallback)
     * <p>
     * Expected Validation Outcome:
     * - Should PASS PRODUCTION validation
     * - Requires chunker and echo services to be registered
     *
     * @return A {@link PipelineClusterConfig} instance with all test pipelines
     */
    public static PipelineClusterConfig createTestCluster() {
        PipelineConfig filesystemPipeline = createFilesystemPipeline();
        PipelineConfig echoPipeline = createEchoPipeline();
        PipelineConfig defaultPipeline = createDefaultPipeline();
        
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of(
                "filesystem-pipeline", filesystemPipeline,
                "echo-pipeline", echoPipeline,
                "default-pipeline", defaultPipeline
            )
        );
        
        // NOTE: allowedGrpcServices must include ALL services referenced in the pipelines
        // This is critical for passing StepReferenceValidator in PRODUCTION mode
        Set<String> allowedServices = Set.of("chunker", "embedder");
        
        return new PipelineClusterConfig(
            "test-cluster",
            graphConfig,
            null, // pipelineModuleMap not needed for basic testing
            "default-pipeline", // Default pipeline for unknown connectors
            Collections.emptySet(), // allowedKafkaTopics (none used in these simple pipelines)
            allowedServices // allowedGrpcServices - CRITICAL for validation
        );
    }
    
    /**
     * Creates a cluster configuration specifically for chunker testing.
     * This focuses on the chunker service with proper configuration.
     * <p>
     * Expected Validation Outcome:
     * - Should PASS PRODUCTION validation
     * - Optimized for chunker service testing scenarios
     *
     * @return A {@link PipelineClusterConfig} instance optimized for chunker testing
     */
    public static PipelineClusterConfig createChunkerTestCluster() {
        PipelineConfig filesystemPipeline = createFilesystemPipeline();
        PipelineConfig defaultPipeline = createDefaultPipeline();
        
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
            Map.of(
                "filesystem-pipeline", filesystemPipeline,
                "default-pipeline", defaultPipeline
            )
        );
        
        return new PipelineClusterConfig(
            "chunker-test-cluster",
            graphConfig,
            null,
            "default-pipeline",
            Collections.emptySet(),
            Set.of("chunker", "embedder") // Both services needed for filesystem-pipeline
        );
    }
    
    /**
     * Helper method to create properly configured PipelineStepConfig instances.
     * Follows the exact same pattern as MockPipelineGenerator.createStep().
     * 
     * @param name The step name (unique within the pipeline)
     * @param type The step type (CONNECTOR, PIPELINE, or SINK)
     * @param processorInfo The processor information (contains grpcServiceName)
     * @param kafkaInputs List of Kafka input definitions (can be empty)
     * @param outputs Map of output targets (can be empty for SINK steps)
     * @return A properly configured {@link PipelineStepConfig} instance
     */
    private static PipelineStepConfig createStep(String name, StepType type, ProcessorInfo processorInfo,
                                          List<KafkaInputDefinition> kafkaInputs,
                                          Map<String, OutputTarget> outputs) {
        return new PipelineStepConfig(
                name, 
                type, 
                "Test Step " + name, // description
                null, // deprecationNotice
                new JsonConfigOptions(JsonNodeFactory.instance.objectNode(), Collections.emptyMap()), // customConfig
                kafkaInputs != null ? kafkaInputs : Collections.emptyList(), // kafkaInputs
                outputs != null ? outputs : Collections.emptyMap(), // outputs
                3, // maxRetries - reasonable default
                1000L, // retryBackoffMs - 1 second
                30000L, // stepTimeoutMs - 30 seconds
                2.0, // backoffMultiplier
                null, // retryPolicy - use default
                processorInfo
        );
    }
}