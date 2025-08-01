package io.pipeline.consul.client.service;

import io.pipeline.api.model.*;
import io.pipeline.api.service.ClusterService;
import io.pipeline.api.service.PipelineConfigService;
import io.pipeline.api.validation.ValidationResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for PipelineConfigService testing.
 * Contains all test logic that can be shared between unit tests and integration tests.
 */
public abstract class PipelineConfigServiceTestBase {
    private static final Logger LOG = Logger.getLogger(PipelineConfigServiceTestBase.class);

    private PipelineConfig testConfig;

    protected abstract PipelineConfigService getPipelineConfigService();
    protected abstract ClusterService getClusterService();

    @BeforeEach
    void setup() {
        // First, ensure clusters are cleaned up from any previous runs
        cleanupClusters();

        // Create test pipeline config
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
            "test-service"
        );

        // Create a Kafka transport config for the output
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
                "test-pipeline.test-step.input", // topic
                null, // default partitionKeyField
                null, // default compressionType
                null, // default batchSize
                null, // default lingerMs
                null  // default kafkaProducerProperties
        );

        // Create an output target
        PipelineStepConfig.OutputTarget outputTarget = new PipelineStepConfig.OutputTarget(
                "sink-step", // targetStepName
                TransportType.KAFKA, // transportType
                null, // grpcTransport (not needed for KAFKA)
                kafkaTransport
        );

        // Create the step with the output
        PipelineStepConfig step = new PipelineStepConfig(
                "test-step",
                StepType.CONNECTOR,
                "Test step description",
                null, // customConfigSchemaId
                null, // customConfig
                Map.of("default", outputTarget), // outputs
                null, // maxRetries
                null, // retryBackoffMs
                null, // maxRetryBackoffMs
                null, // retryBackoffMultiplier
                null, // stepTimeoutMs
                processorInfo
        );

        testConfig = new PipelineConfig(
                "test-pipeline",
                Map.of("test-step", step)
        );
    }

    @AfterEach
    void cleanup() {
        LOG.info("Cleaning up after test");

        // Clean up pipelines first, then clusters
        cleanupPipelines();
        cleanupClusters();
    }

    private void cleanupPipelines() {
        // Clean up all pipelines that might have been created during tests
        String[] pipelineNames = {
                "test-pipeline",
                "document-processing-pipeline",
                "empty-pipeline",
                "pipeline-1",
                "pipeline-2",
                "pipeline-3",
                "concurrent-pipeline",
                "update-test-pipeline"
        };

        String[] clusterNames = {
                "test-cluster",
                "concurrent-cluster",
                "update-cluster"
        };

        // For each cluster, try to clean up all pipelines
        for (String clusterName : clusterNames) {
            for (String pipelineName : pipelineNames) {
                try {
                    // Check if pipeline exists
                    Optional<PipelineConfig> pipeline = getPipelineConfigService()
                            .getPipeline(clusterName, pipelineName)
                            .await().atMost(Duration.ofSeconds(2));

                    if (pipeline.isPresent()) {
                        LOG.infof("Deleting pipeline: %s in cluster: %s", pipelineName, clusterName);
                        ValidationResult result = getPipelineConfigService()
                                .deletePipeline(clusterName, pipelineName)
                                .await().atMost(Duration.ofSeconds(2));

                        if (!result.valid()) {
                            LOG.warnf("Failed to delete pipeline %s: %s", pipelineName, result.errors());
                        }
                    }
                } catch (Exception e) {
                    // Ignore errors - pipeline might not exist
                    LOG.debugf("Pipeline %s in cluster %s doesn't exist or error during cleanup: %s",
                            pipelineName, clusterName, e.getMessage());
                }
            }
        }
    }

    private void cleanupClusters() {
        // Clean up any clusters that might have been created
        String[] clusterNames = {
                "test-cluster",
                "concurrent-cluster",
                "update-cluster"
        };

        for (String clusterName : clusterNames) {
            try {
                // Try to delete the cluster
                ValidationResult result = getClusterService()
                        .deleteCluster(clusterName)
                        .await().atMost(Duration.ofSeconds(2));

                if (result.valid()) {
                    LOG.infof("Successfully deleted cluster: %s", clusterName);
                } else {
                    LOG.debugf("Cluster %s doesn't exist or couldn't be deleted: %s",
                            clusterName, result.errors());
                }
            } catch (Exception e) {
                // Ignore - cluster might not exist
                LOG.debugf("Error cleaning up cluster %s: %s", clusterName, e.getMessage());
            }
        }
    }


    @Test
    void testCreateCluster() {
        // TODO: Start simple - create cluster first
        // This should be a service call to create/register a cluster
        LOG.info("TODO: Implement cluster creation service call");

        // For now, just verify we can interact with Consul
        // Later this will create the cluster structure in Consul
        String clusterName = "test-cluster";
        String clusterKey = "rokkon-clusters/" + clusterName + "/metadata";

        // TODO: Create ClusterService to handle cluster operations
        // TODO: Store cluster metadata (name, created date, etc)
        assertNotNull(clusterKey);
    }

    @Test
    void testCreateSimplePipeline() {
        // Create a minimal valid pipeline that meets validator requirements
        LOG.info("Starting simple pipeline creation test");

        String clusterName = "test-cluster";

        // Create empty pipeline - no steps initially
        // Services need to be registered first before adding steps
        PipelineConfig emptyPipeline = new PipelineConfig(
                "empty-pipeline",
                Map.of() // No steps yet
        );

        LOG.infof("Created empty pipeline config: %s", emptyPipeline);

        // Test creation
        ValidationResult result = getPipelineConfigService().createPipeline(
                clusterName, "empty-pipeline", emptyPipeline
        ).await().indefinitely();

        LOG.infof("Pipeline creation result: valid=%s, errors=%s, warnings=%s",
                result.valid(), result.errors(), result.warnings());

        assertNotNull(result);

        // If validation fails, log what's required
        if (!result.valid()) {
            LOG.error("Validation failed. Errors:");
            for (String error : result.errors()) {
                LOG.errorf("  - %s", error);
            }
        }

        assertTrue(result.errors().isEmpty(), "Validation errors should be empty");
        assertTrue(result.valid(), "Empty pipeline should be valid");

        // Verify it was stored
        Optional<PipelineConfig> retrieved = getPipelineConfigService().getPipeline(clusterName, "empty-pipeline")
                .await().indefinitely();
        assertTrue(retrieved.isPresent());
        assertEquals("empty-pipeline", retrieved.get().name());
        assertTrue(retrieved.get().pipelineSteps().isEmpty());
    }

    @Test
    void testCreatePipelineWithConsul() {
        // Create a minimal valid pipeline config that meets all validator requirements
        // 1. Must have INITIAL_PIPELINE step
        // 2. Must have SINK step
        // 3. Must have valid naming (lowercase-with-hyphens)
        // 4. Must have all required fields

        // Create INITIAL_PIPELINE step with all required fields
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
                "document-ingestion-service" // Valid service name format
        );

        // Create a Kafka transport config for the source step output
        KafkaTransportConfig sourceKafkaTransport = new KafkaTransportConfig(
                "document-processing-pipeline.document-source.input", // topic
                null, // default partitionKeyField
                null, // default compressionType
                null, // default batchSize
                null, // default lingerMs
                null  // default kafkaProducerProperties
        );

        // Create an output target for the source step
        PipelineStepConfig.OutputTarget sourceOutputTarget = new PipelineStepConfig.OutputTarget(
                "document-sink", // targetStepName
                TransportType.KAFKA, // transportType
                null, // grpcTransport (not needed for KAFKA)
                sourceKafkaTransport
        );

        // Create the source step with the output
        PipelineStepConfig sourceStep = new PipelineStepConfig(
                "document-source", // Valid step name format
                StepType.CONNECTOR,
                "Document source step",
                null, // customConfigSchemaId
                null, // customConfig
                Map.of("default", sourceOutputTarget), // outputs
                null, // maxRetries
                null, // retryBackoffMs
                null, // maxRetryBackoffMs
                null, // retryBackoffMultiplier
                null, // stepTimeoutMs
                sourceProcessor
        );

        // Create SINK step with all required fields
        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
                "document-sink-service"// Valid service name format
        );

        PipelineStepConfig sinkStep = new PipelineStepConfig(
                "document-sink", // Valid step name format
                StepType.SINK,
                sinkProcessor
        );

        // Create pipeline config with valid name
        PipelineConfig validConfig = new PipelineConfig(
                "document-processing-pipeline", // Valid pipeline name format
                Map.of(
                        "document-source", sourceStep,
                        "document-sink", sinkStep
                )
        );

        LOG.infof("Creating pipeline with config: %s", validConfig);

        // Create pipeline
        ValidationResult validationResult = getPipelineConfigService().createPipeline(
                "test-cluster", "document-processing-pipeline", validConfig
        ).await().indefinitely();
        LOG.infof("Validation result: valid=%s, errors=%s",
                validationResult.valid(), validationResult.errors());

        // If validation fails, log specific errors to understand what's wrong
        if (!validationResult.valid()) {
            LOG.errorf("Validation failed with errors: %s", validationResult.errors());
            for (String error : validationResult.errors()) {
                LOG.errorf("  - %s", error);
            }
        }

        assertNotNull(validationResult);
        assertTrue(validationResult.errors().isEmpty(), "Validation errors should be empty");
        assertTrue(validationResult.valid(), "Validation should pass");

        // Verify it was stored
        Optional<PipelineConfig> retrieved = getPipelineConfigService().getPipeline("test-cluster", "document-processing-pipeline")
                .await().indefinitely();
        assertTrue(retrieved.isPresent());
        assertEquals("document-processing-pipeline", retrieved.get().name());
    }

    @Test
    void testUpdatePipeline() {
        // First create a pipeline
        testCreatePipelineWithConsul();

        // Update it
        PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo(
            "updated-service"
        );
        PipelineStepConfig updatedStep = new PipelineStepConfig(
                "updated-step",
                StepType.PIPELINE,
                processor
        );

        // Need INITIAL_PIPELINE and SINK for valid config
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "source-service"
        );

        // Create a Kafka transport config for the source step output
        KafkaTransportConfig sourceKafkaTransport = new KafkaTransportConfig(
                "document-processing-pipeline.document-source.input", // topic
                null, // default partitionKeyField
                null, // default compressionType
                null, // default batchSize
                null, // default lingerMs
                null  // default kafkaProducerProperties
        );

        // Create an output target for the source step
        PipelineStepConfig.OutputTarget sourceOutputTarget = new PipelineStepConfig.OutputTarget(
                "document-sink", // targetStepName
                TransportType.KAFKA, // transportType
                null, // grpcTransport (not needed for KAFKA)
                sourceKafkaTransport
        );

        // Create the source step with the output
        PipelineStepConfig sourceStep = new PipelineStepConfig(
                "document-source",
                StepType.CONNECTOR,
                "Source step description",
                null, // customConfigSchemaId
                null, // customConfig
                Map.of("default", sourceOutputTarget), // outputs
                null, // maxRetries
                null, // retryBackoffMs
                null, // maxRetryBackoffMs
                null, // retryBackoffMultiplier
                null, // stepTimeoutMs
                sourceProcessor
        );

        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "sink-service"
        );
        PipelineStepConfig sinkStep = new PipelineStepConfig(
                "document-sink",
                StepType.SINK,
                sinkProcessor
        );

        PipelineConfig updatedConfig = new PipelineConfig(
                "document-processing-pipeline",
                Map.of(
                        "document-source", sourceStep,
                        "updated-step", updatedStep,
                        "document-sink", sinkStep
                )
        );

        ValidationResult validationResult = getPipelineConfigService().updatePipeline(
                "test-cluster", "document-processing-pipeline", updatedConfig
        ).await().indefinitely();
        assertTrue(validationResult.valid());

        // Verify update
        Optional<PipelineConfig> retrieved = getPipelineConfigService().getPipeline("test-cluster", "document-processing-pipeline")
                .await().indefinitely();
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().pipelineSteps().containsKey("updated-step"));
    }

    @Test
    void testDeletePipeline() {
        // First create a pipeline
        testCreatePipelineWithConsul();

        // Delete it
        ValidationResult validationResult = getPipelineConfigService().deletePipeline(
                "test-cluster", "document-processing-pipeline"
        ).await().indefinitely();
        assertTrue(validationResult.valid());

        // Verify it's gone
        Optional<PipelineConfig> retrieved = getPipelineConfigService().getPipeline("test-cluster", "document-processing-pipeline")
                .await().indefinitely();
        assertTrue(retrieved.isEmpty());
    }

    @Test
    void testListPipelines() {
        // Create multiple pipelines
        for (int i = 1; i <= 3; i++) {
            PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
                    "source-service-" + i);

            // Create a Kafka transport config for the source step output
            KafkaTransportConfig sourceKafkaTransport = new KafkaTransportConfig(
                    "pipeline-" + i + ".source-step.input", // topic
                    null, // default partitionKeyField
                    null, // default compressionType
                    null, // default batchSize
                    null, // default lingerMs
                    null  // default kafkaProducerProperties
            );

            // Create an output target for the source step
            PipelineStepConfig.OutputTarget sourceOutputTarget = new PipelineStepConfig.OutputTarget(
                    "sink-step", // targetStepName
                    TransportType.KAFKA, // transportType
                    null, // grpcTransport (not needed for KAFKA)
                    sourceKafkaTransport
            );

            // Create the source step with the output
            PipelineStepConfig sourceStep = new PipelineStepConfig(
                    "source-step",
                    StepType.CONNECTOR,
                    "Source step " + i,
                    null, // customConfigSchemaId
                    null, // customConfig
                    Map.of("default", sourceOutputTarget), // outputs
                    null, // maxRetries
                    null, // retryBackoffMs
                    null, // maxRetryBackoffMs
                    null, // retryBackoffMultiplier
                    null, // stepTimeoutMs
                    sourceProcessor
            );

            PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
                    "sink-service-" + i);
            PipelineStepConfig sinkStep = new PipelineStepConfig(
                    "sink-step",
                    StepType.SINK,
                    sinkProcessor
            );

            PipelineConfig config = new PipelineConfig(
                    "pipeline-" + i,
                    Map.of(
                            "source-step", sourceStep,
                            "sink-step", sinkStep
                    )
            );

            getPipelineConfigService().createPipeline("test-cluster", "pipeline-" + i, config)
                    .await().indefinitely();
        }

        // List them
        Map<String, PipelineConfig> pipelines = getPipelineConfigService().listPipelines("test-cluster")
                .await().indefinitely();

        assertEquals(3, pipelines.size());
        assertAll("Should contain all created pipeline keys",
                () -> assertTrue(pipelines.containsKey("pipeline-1")),
                () -> assertTrue(pipelines.containsKey("pipeline-2")),
                () -> assertTrue(pipelines.containsKey("pipeline-3"))
        );
    }

    @Test
    void testConcurrentPipelineCreation() {
        int numAttempts = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Create valid config
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "source-service"
        );

        // Create a Kafka transport config for the source step output
        KafkaTransportConfig sourceKafkaTransport = new KafkaTransportConfig(
                "concurrent-pipeline.source-step.input", // topic
                null, // default partitionKeyField
                null, // default compressionType
                null, // default batchSize
                null, // default lingerMs
                null  // default kafkaProducerProperties
        );

        // Create an output target for the source step
        PipelineStepConfig.OutputTarget sourceOutputTarget = new PipelineStepConfig.OutputTarget(
                "sink-step", // targetStepName
                TransportType.KAFKA, // transportType
                null, // grpcTransport (not needed for KAFKA)
                sourceKafkaTransport
        );

        // Create the source step with the output
        PipelineStepConfig sourceStep = new PipelineStepConfig(
                "source-step",
                StepType.CONNECTOR,
                "Concurrent source step",
                null, // customConfigSchemaId
                null, // customConfig
                Map.of("default", sourceOutputTarget), // outputs
                null, // maxRetries
                null, // retryBackoffMs
                null, // maxRetryBackoffMs
                null, // retryBackoffMultiplier
                null, // stepTimeoutMs
                sourceProcessor
        );

        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "sink-service"
        );
        PipelineStepConfig sinkStep = new PipelineStepConfig(
                "sink-step",
                StepType.SINK,
                sinkProcessor
        );

        PipelineConfig config = new PipelineConfig(
                "concurrent-pipeline",
                Map.of(
                        "source-step", sourceStep,
                        "sink-step", sinkStep
                )
        );

        // Use Mutiny Multi to create concurrent attempts
        List<Uni<ValidationResult>> creationAttempts = IntStream.range(0, numAttempts)
                .mapToObj(i -> getPipelineConfigService().createPipeline("concurrent-cluster", "concurrent-pipeline", config))
                .toList();

        // Execute all concurrently and collect results
        Multi.createFrom().iterable(creationAttempts)
                .onItem().transformToUniAndMerge(uni -> uni)
                .onItem().invoke(result -> {
                    if (result.valid()) {
                        successCount.incrementAndGet();
                    } else if (result.errors().stream()
                            .anyMatch(e -> e.contains("already exists"))) {
                        conflictCount.incrementAndGet();
                    }
                })
                .collect().asList()
                .await().atMost(Duration.ofSeconds(10));

        // Verify at least one succeeded and the rest failed with conflicts
        assertTrue(successCount.get() >= 1);
        assertEquals(numAttempts, successCount.get() + conflictCount.get());

        // Verify pipeline exists using Awaitility
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Optional<PipelineConfig> retrieved = getPipelineConfigService().getPipeline("concurrent-cluster", "concurrent-pipeline")
                            .await().indefinitely();
                    assertTrue(retrieved.isPresent());
                });

        // Cleanup
        getPipelineConfigService().deletePipeline("concurrent-cluster", "concurrent-pipeline")
                .await().indefinitely();
    }

    @Test
    void testConcurrentPipelineUpdates() {
        // First create a pipeline
        PipelineStepConfig.ProcessorInfo sourceProcessor = new PipelineStepConfig.ProcessorInfo(
            "source-service"
        );

        // Create a Kafka transport config for the source step output
        KafkaTransportConfig sourceKafkaTransport = new KafkaTransportConfig(
                "update-test-pipeline.source-step.input", // topic
                null, // default partitionKeyField
                null, // default compressionType
                null, // default batchSize
                null, // default lingerMs
                null  // default kafkaProducerProperties
        );

        // Create an output target for the source step
        PipelineStepConfig.OutputTarget sourceOutputTarget = new PipelineStepConfig.OutputTarget(
                "sink-step", // targetStepName
                TransportType.KAFKA, // transportType
                null, // grpcTransport (not needed for KAFKA)
                sourceKafkaTransport
        );

        // Create the source step with the output
        PipelineStepConfig sourceStep = new PipelineStepConfig(
                "source-step",
                StepType.CONNECTOR,
                "Update test source step",
                null, // customConfigSchemaId
                null, // customConfig
                Map.of("default", sourceOutputTarget), // outputs
                null, // maxRetries
                null, // retryBackoffMs
                null, // maxRetryBackoffMs
                null, // retryBackoffMultiplier
                null, // stepTimeoutMs
                sourceProcessor
        );

        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
            "sink-service"
        );
        PipelineStepConfig sinkStep = new PipelineStepConfig(
                "sink-step",
                StepType.SINK,
                sinkProcessor
        );

        PipelineConfig initialConfig = new PipelineConfig(
                "update-test-pipeline",
                Map.of(
                        "source-step", sourceStep,
                        "sink-step", sinkStep
                )
        );

        getPipelineConfigService().createPipeline("update-cluster", "update-test-pipeline", initialConfig)
                .await().indefinitely();

        // Now try concurrent updates
        int numUpdates = 5;
        AtomicInteger updateCount = new AtomicInteger(0);

        // Create update Unis
        List<Uni<ValidationResult>> updateAttempts = IntStream.range(0, numUpdates)
                .mapToObj(i -> {
                    // Each update adds its own step
                    PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo(
                            "processor-" + i);
                    PipelineStepConfig newStep = new PipelineStepConfig(
                            "step-" + i,
                            StepType.PIPELINE,
                            processor
                    );

                    PipelineConfig updatedConfig = new PipelineConfig(
                            "update-test-pipeline",
                            Map.of(
                                    "source-step", sourceStep,
                                    "step-" + i, newStep,
                                    "sink-step", sinkStep
                            )
                    );

                    return getPipelineConfigService().updatePipeline("update-cluster", "update-test-pipeline", updatedConfig);
                })
                .toList();

        // Execute all updates concurrently
        Multi.createFrom().iterable(updateAttempts)
                .onItem().transformToUniAndMerge(uni -> uni)
                .onItem().invoke(result -> {
                    if (result.valid()) {
                        updateCount.incrementAndGet();
                    }
                })
                .collect().asList()
                .await().atMost(Duration.ofSeconds(10));

        // All updates should succeed (last one wins in Consul)
        assertEquals(numUpdates, updateCount.get());

        // Verify pipeline exists and has been updated
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Optional<PipelineConfig> retrieved = getPipelineConfigService().getPipeline("update-cluster", "update-test-pipeline")
                            .await().indefinitely();
                    assertTrue(retrieved.isPresent());
                    // The pipeline should have at least one of the updated steps
                    assertTrue(retrieved.get().pipelineSteps().keySet().stream()
                            .anyMatch(key -> key.startsWith("step-")));
                });

        // Cleanup
        getPipelineConfigService().deletePipeline("update-cluster", "update-test-pipeline")
                .await().indefinitely();
    }
}
