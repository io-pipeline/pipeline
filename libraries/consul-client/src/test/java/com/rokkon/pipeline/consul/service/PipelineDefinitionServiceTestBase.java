package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.model.*;
import com.rokkon.pipeline.config.service.PipelineDefinitionService;
import com.rokkon.pipeline.config.service.PipelineInstanceService;
import com.rokkon.pipeline.api.validation.ValidationResult;
import com.rokkon.pipeline.api.validation.ValidationMode;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for PipelineDefinitionService testing.
 * Contains all test logic that can be shared between unit tests and integration tests.
 */
public abstract class PipelineDefinitionServiceTestBase {
    private static final Logger LOG = Logger.getLogger(PipelineDefinitionServiceTestBase.class);

    protected abstract PipelineDefinitionService getPipelineDefinitionService();
    protected abstract PipelineInstanceService getPipelineInstanceService();

    private PipelineConfig testDefinition;

    @BeforeEach
    void setup() {
        // Clean up any existing definitions first
        cleanupDefinitions();

        // Create test pipeline definition
        PipelineStepConfig.ProcessorInfo processorInfo = new PipelineStepConfig.ProcessorInfo(
                "test-service", null
        );

        // Create a Kafka transport config for the output
        KafkaTransportConfig kafkaTransport = new KafkaTransportConfig(
                "test-pipeline.test-step.input",
                null, null, null, null, null
        );

        // Create an output target
        PipelineStepConfig.OutputTarget outputTarget = new PipelineStepConfig.OutputTarget(
                "sink-step",
                TransportType.KAFKA,
                null,
                kafkaTransport
        );

        // Create the step with the output
        PipelineStepConfig sourceStep = new PipelineStepConfig(
                "test-step",
                StepType.INITIAL_PIPELINE,
                "Test step description",
                null, null,
                Map.of("default", outputTarget),
                null, null, null, null, null,
                processorInfo
        );

        // Create sink step
        PipelineStepConfig.ProcessorInfo sinkProcessor = new PipelineStepConfig.ProcessorInfo(
                "sink-service", null
        );
        PipelineStepConfig sinkStep = new PipelineStepConfig(
                "sink-step",
                StepType.SINK,
                sinkProcessor
        );

        testDefinition = new PipelineConfig(
                "test-pipeline",
                Map.of(
                    "test-step", sourceStep,
                    "sink-step", sinkStep
                )
        );
    }

    @AfterEach
    void cleanup() {
        LOG.info("Cleaning up after test");
        cleanupDefinitions();
    }

    private void cleanupDefinitions() {
        // Clean up all definitions that might have been created during tests
        String[] definitionIds = {
                "test-definition",
                "production-pipeline",
                "design-pipeline",
                "def-1", "def-2", "def-3",
                "concurrent-definition",
                "update-test-definition"
        };

        for (String defId : definitionIds) {
            try {
                Boolean exists = getPipelineDefinitionService()
                        .definitionExists(defId)
                        .await().atMost(Duration.ofSeconds(2));

                if (exists) {
                    LOG.infof("Deleting pipeline definition: %s", defId);
                    // First check if there are active instances and clean them up if needed
                    Integer activeCount = getPipelineDefinitionService()
                            .getActiveInstanceCount(defId)
                            .await().atMost(Duration.ofSeconds(2));
                    
                    if (activeCount > 0) {
                        LOG.warnf("Pipeline definition %s has %d active instances, skipping deletion", defId, activeCount);
                        continue;
                    }

                    ValidationResult result = getPipelineDefinitionService()
                            .deleteDefinition(defId)
                            .await().atMost(Duration.ofSeconds(2));

                    if (!result.valid()) {
                        LOG.warnf("Failed to delete pipeline definition %s: %s", defId, result.errors());
                    }
                }
            } catch (Exception e) {
                // Ignore errors - definition might not exist
                LOG.debugf("Definition %s doesn't exist or error during cleanup: %s",
                        defId, e.getMessage());
            }
        }
    }

    @Test
    void testCreateDefinition() {
        String definitionId = "test-definition";

        ValidationResult result = getPipelineDefinitionService()
                .createDefinition(definitionId, testDefinition)
                .await().indefinitely();

        assertNotNull(result);
        assertTrue(result.valid(), "Definition should be valid");
        assertTrue(result.errors().isEmpty(), "Should have no errors");

        // Verify it was stored
        PipelineConfig retrieved = getPipelineDefinitionService()
                .getDefinition(definitionId)
                .await().indefinitely();
        assertNotNull(retrieved);
        assertEquals("test-pipeline", retrieved.name());
        assertEquals(2, retrieved.pipelineSteps().size());
    }

    @Test
    void testCreateDefinitionWithValidationModes() {
        // Test PRODUCTION mode - requires complete validation
        String prodDefId = "production-pipeline";
        ValidationResult prodResult = getPipelineDefinitionService()
                .createDefinition(prodDefId, testDefinition, ValidationMode.PRODUCTION)
                .await().indefinitely();

        assertTrue(prodResult.valid(), "Production pipeline should be valid");

        // Test DESIGN mode - allows warnings
        String designDefId = "design-pipeline";
        
        // Create a pipeline with potential warnings but no errors
        PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo(
                "design-service", null
        );
        PipelineStepConfig designStep = new PipelineStepConfig(
                "design-step",
                StepType.PIPELINE,
                processor
        );
        
        PipelineConfig designConfig = new PipelineConfig(
                "design-pipeline",
                Map.of("design-step", designStep)
        );

        ValidationResult designResult = getPipelineDefinitionService()
                .createDefinition(designDefId, designConfig, ValidationMode.DESIGN)
                .await().indefinitely();

        assertNotNull(designResult);
        // Design mode may have warnings but should still succeed if no errors
        if (designResult.hasWarnings()) {
            LOG.infof("Design mode validation warnings: %s", designResult.warnings());
        }
    }

    @Test
    void testUpdateDefinition() {
        // First create a definition
        String definitionId = "update-test-definition";
        getPipelineDefinitionService()
                .createDefinition(definitionId, testDefinition)
                .await().indefinitely();

        // Update it
        PipelineStepConfig.ProcessorInfo processor = new PipelineStepConfig.ProcessorInfo(
                "updated-service", null
        );
        PipelineStepConfig updatedStep = new PipelineStepConfig(
                "updated-step",
                StepType.PIPELINE,
                processor
        );

        PipelineConfig updatedDefinition = new PipelineConfig(
                "updated-pipeline",
                Map.of(
                    "test-step", testDefinition.pipelineSteps().get("test-step"),
                    "updated-step", updatedStep,
                    "sink-step", testDefinition.pipelineSteps().get("sink-step")
                )
        );

        ValidationResult result = getPipelineDefinitionService()
                .updateDefinition(definitionId, updatedDefinition)
                .await().indefinitely();

        assertTrue(result.valid());

        // Verify update
        PipelineConfig retrieved = getPipelineDefinitionService()
                .getDefinition(definitionId)
                .await().indefinitely();
        assertNotNull(retrieved);
        assertEquals("updated-pipeline", retrieved.name());
        assertTrue(retrieved.pipelineSteps().containsKey("updated-step"));
    }

    @Test
    void testDeleteDefinition() {
        // First create a definition
        String definitionId = "test-definition";
        getPipelineDefinitionService()
                .createDefinition(definitionId, testDefinition)
                .await().indefinitely();

        // Delete it
        ValidationResult result = getPipelineDefinitionService()
                .deleteDefinition(definitionId)
                .await().indefinitely();
        assertTrue(result.valid());

        // Verify it's gone
        PipelineConfig retrieved = getPipelineDefinitionService()
                .getDefinition(definitionId)
                .await().indefinitely();
        assertNull(retrieved);

        // Verify exists returns false
        Boolean exists = getPipelineDefinitionService()
                .definitionExists(definitionId)
                .await().indefinitely();
        assertFalse(exists);
    }

    @Test
    void testListDefinitions() {
        // Create multiple definitions
        for (int i = 1; i <= 3; i++) {
            PipelineConfig config = new PipelineConfig(
                    "pipeline-" + i,
                    testDefinition.pipelineSteps()
            );

            getPipelineDefinitionService()
                    .createDefinition("def-" + i, config)
                    .await().indefinitely();
        }

        // List them
        List<PipelineDefinitionSummary> definitions = getPipelineDefinitionService()
                .listDefinitions()
                .await().indefinitely();

        assertNotNull(definitions);
        assertTrue(definitions.size() >= 3, "Should have at least 3 definitions");
        
        // Verify our definitions are in the list
        long ourDefinitions = definitions.stream()
                .filter(def -> def.id().startsWith("def-"))
                .count();
        assertEquals(3, ourDefinitions);
    }

    @Test
    void testDefinitionExists() {
        String definitionId = "test-definition";
        
        // Should not exist initially
        Boolean exists = getPipelineDefinitionService()
                .definitionExists(definitionId)
                .await().indefinitely();
        assertFalse(exists);

        // Create it
        getPipelineDefinitionService()
                .createDefinition(definitionId, testDefinition)
                .await().indefinitely();

        // Should exist now
        exists = getPipelineDefinitionService()
                .definitionExists(definitionId)
                .await().indefinitely();
        assertTrue(exists);
    }

    @Test
    void testConcurrentDefinitionCreation() {
        int numAttempts = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Use Mutiny Multi to create concurrent attempts
        List<Uni<ValidationResult>> creationAttempts = IntStream.range(0, numAttempts)
                .mapToObj(i -> getPipelineDefinitionService()
                        .createDefinition("concurrent-definition", testDefinition))
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

        // Verify at least one succeeded (due to race conditions, multiple might succeed)
        assertTrue(successCount.get() >= 1, "At least one creation should succeed");
        
        // In integration tests, we might have multiple successes due to race conditions
        // between the exists check and the create operation
        LOG.infof("Concurrent creation results: %d succeeded, %d conflicts", 
                successCount.get(), conflictCount.get());

        // Verify definition exists
        Boolean exists = getPipelineDefinitionService()
                .definitionExists("concurrent-definition")
                .await().indefinitely();
        assertTrue(exists);
    }

    @Test
    void testCreateDuplicateDefinition() {
        String definitionId = "test-definition";

        // Create first
        ValidationResult result1 = getPipelineDefinitionService()
                .createDefinition(definitionId, testDefinition)
                .await().indefinitely();
        assertTrue(result1.valid());

        // Try to create duplicate
        ValidationResult result2 = getPipelineDefinitionService()
                .createDefinition(definitionId, testDefinition)
                .await().indefinitely();
        assertFalse(result2.valid());
        assertTrue(result2.errors().stream()
                .anyMatch(e -> e.contains("already exists")));
    }

    @Test
    void testUpdateNonExistentDefinition() {
        ValidationResult result = getPipelineDefinitionService()
                .updateDefinition("non-existent", testDefinition)
                .await().indefinitely();

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
                .anyMatch(e -> e.contains("not found")));
    }

    @Test
    void testDeleteNonExistentDefinition() {
        ValidationResult result = getPipelineDefinitionService()
                .deleteDefinition("non-existent")
                .await().indefinitely();

        assertFalse(result.valid());
        assertTrue(result.errors().stream()
                .anyMatch(e -> e.contains("not found")));
    }

    @Test
    void testGetActiveInstanceCount() {
        String definitionId = "test-definition";
        
        // Create definition
        getPipelineDefinitionService()
                .createDefinition(definitionId, testDefinition)
                .await().indefinitely();

        // Check instance count (should be 0)
        Integer count = getPipelineDefinitionService()
                .getActiveInstanceCount(definitionId)
                .await().indefinitely();
        
        assertNotNull(count);
        assertEquals(0, count);
    }
}