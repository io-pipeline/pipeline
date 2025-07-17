package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.model.CreateInstanceRequest;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineInstance;
import com.rokkon.pipeline.config.service.PipelineDefinitionService;
import com.rokkon.pipeline.config.service.PipelineInstanceService;
import com.rokkon.pipeline.consul.test.UnifiedTestBase;
import com.rokkon.pipeline.api.validation.ValidationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for PipelineInstanceService implementations.
 * Provides common test methods that can be used by both unit and integration tests.
 */
abstract class PipelineInstanceServiceTestBase extends UnifiedTestBase {
    
    protected PipelineInstanceService pipelineInstanceService;
    protected PipelineDefinitionService pipelineDefinitionService;
    protected String testClusterName;
    protected List<String> testInstanceIds = new CopyOnWriteArrayList<>();
    protected List<String> testDefinitionIds = new CopyOnWriteArrayList<>();
    
    /**
     * Subclasses must implement this to set up test dependencies
     */
    abstract void setupDependencies();
    
    @BeforeEach
    void setUp() {
        setupDependencies();
        testClusterName = "test-cluster-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    @AfterEach
    void cleanup() {
        // Clean up test instances
        for (String instanceId : testInstanceIds) {
            try {
                pipelineInstanceService.deleteInstance(testClusterName, instanceId)
                    .await().atMost(Duration.ofSeconds(5));
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        testInstanceIds.clear();
        testDefinitionIds.clear();
    }
    
    @Test
    void testCreateInstance() {
        // First create a pipeline definition
        String defId = createTestDefinition("test-pipeline", "Test Pipeline");
        
        // Create instance request
        String instanceId = "test-instance-" + UUID.randomUUID().toString().substring(0, 8);
        testInstanceIds.add(instanceId);
        
        CreateInstanceRequest request = new CreateInstanceRequest(
            instanceId,
            defId,
            "Test Instance",
            "A test pipeline instance",
            "test-topic-",
            1,
            4,
            Map.of(),
            Map.of("env", "test")
        );
        
        // Create instance
        ValidationResult result = pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        
        assertTrue(result.valid());
        
        // Verify instance was created
        PipelineInstance instance = pipelineInstanceService.getInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertNotNull(instance);
        assertEquals(instanceId, instance.instanceId());
        assertEquals(defId, instance.pipelineDefinitionId());
        assertEquals("Test Instance", instance.name());
        assertEquals(PipelineInstance.PipelineInstanceStatus.STOPPED, instance.status());
    }
    
    @Test
    void testCreateDuplicateInstance() {
        // Create a pipeline definition
        String defId = createTestDefinition("dup-pipeline", "Duplicate Test Pipeline");
        
        // Create first instance
        String instanceId = "dup-instance-" + UUID.randomUUID().toString().substring(0, 8);
        testInstanceIds.add(instanceId);
        
        CreateInstanceRequest request = new CreateInstanceRequest(
            instanceId,
            defId,
            "First Instance",
            null,
            null,
            null,
            null,
            null,
            null
        );
        
        ValidationResult result1 = pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        assertTrue(result1.valid());
        
        // Try to create duplicate
        ValidationResult result2 = pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        
        assertFalse(result2.valid());
        assertTrue(result2.errors().stream()
            .anyMatch(e -> e.contains("already exists")));
    }
    
    @Test
    void testCreateInstanceWithInvalidDefinition() {
        String instanceId = "invalid-def-instance-" + UUID.randomUUID().toString().substring(0, 8);
        testInstanceIds.add(instanceId);
        
        CreateInstanceRequest request = new CreateInstanceRequest(
            instanceId,
            "non-existent-definition",
            "Invalid Instance",
            null,
            null,
            null,
            null,
            null,
            null
        );
        
        ValidationResult result = pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("not found")));
    }
    
    @Test
    void testListInstances() {
        // Create multiple instances
        String defId = createTestDefinition("list-pipeline", "List Test Pipeline");
        
        for (int i = 0; i < 3; i++) {
            String instanceId = "list-instance-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            testInstanceIds.add(instanceId);
            
            CreateInstanceRequest request = new CreateInstanceRequest(
                instanceId,
                defId,
                "Instance " + i,
                null,
                null,
                null,
                null,
                null,
                null
            );
            
            pipelineInstanceService.createInstance(testClusterName, request)
                .await().atMost(Duration.ofSeconds(5));
        }
        
        // List instances
        List<PipelineInstance> instances = pipelineInstanceService.listInstances(testClusterName)
            .await().atMost(Duration.ofSeconds(5));
        
        assertEquals(3, instances.size());
        instances.forEach(instance -> {
            assertTrue(instance.instanceId().startsWith("list-instance-"));
            assertEquals(defId, instance.pipelineDefinitionId());
        });
    }
    
    @Test
    void testUpdateInstance() {
        // Create instance
        String defId = createTestDefinition("update-pipeline", "Update Test Pipeline");
        String instanceId = "update-instance-" + UUID.randomUUID().toString().substring(0, 8);
        testInstanceIds.add(instanceId);
        
        CreateInstanceRequest request = new CreateInstanceRequest(
            instanceId,
            defId,
            "Original Name",
            "Original Description",
            null,
            1,
            null,
            null,
            Map.of("version", "1.0")
        );
        
        pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        
        // Update instance
        PipelineInstance original = pipelineInstanceService.getInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        PipelineInstance updated = new PipelineInstance(
            instanceId,
            defId,
            testClusterName,
            "Updated Name",
            "Updated Description",
            original.status(),
            Map.of(),
            "updated-topic-",
            2,
            8,
            Map.of("version", "2.0"),
            original.createdAt(),
            original.modifiedAt(),
            original.startedAt(),
            original.stoppedAt()
        );
        
        ValidationResult result = pipelineInstanceService.updateInstance(testClusterName, instanceId, updated)
            .await().atMost(Duration.ofSeconds(5));
        
        assertTrue(result.valid());
        
        // Verify update
        PipelineInstance retrieved = pipelineInstanceService.getInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertEquals("Updated Name", retrieved.name());
        assertEquals("Updated Description", retrieved.description());
        assertEquals(2, retrieved.priority());
        assertEquals(8, retrieved.maxParallelism());
        assertEquals("2.0", retrieved.metadata().get("version"));
    }
    
    @Test
    void testDeleteInstance() {
        // Create instance
        String defId = createTestDefinition("delete-pipeline", "Delete Test Pipeline");
        String instanceId = "delete-instance-" + UUID.randomUUID().toString().substring(0, 8);
        testInstanceIds.add(instanceId);
        
        CreateInstanceRequest request = new CreateInstanceRequest(
            instanceId,
            defId,
            "To Delete",
            null,
            null,
            null,
            null,
            null,
            null
        );
        
        pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        
        // Delete instance
        ValidationResult result = pipelineInstanceService.deleteInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertTrue(result.valid());
        
        // Verify deletion
        PipelineInstance deleted = pipelineInstanceService.getInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertNull(deleted);
    }
    
    @Test
    void testDeleteRunningInstance() {
        // Create and start instance
        String defId = createTestDefinition("running-pipeline", "Running Test Pipeline");
        String instanceId = "running-instance-" + UUID.randomUUID().toString().substring(0, 8);
        testInstanceIds.add(instanceId);
        
        CreateInstanceRequest request = new CreateInstanceRequest(
            instanceId,
            defId,
            "Running Instance",
            null,
            null,
            null,
            null,
            null,
            null
        );
        
        pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        
        // Start instance
        pipelineInstanceService.startInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        // Try to delete running instance
        ValidationResult result = pipelineInstanceService.deleteInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("Cannot delete running instance")));
        
        // Stop instance for cleanup
        pipelineInstanceService.stopInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
    }
    
    @Test
    void testStartStopInstance() {
        // Create instance
        String defId = createTestDefinition("lifecycle-pipeline", "Lifecycle Test Pipeline");
        String instanceId = "lifecycle-instance-" + UUID.randomUUID().toString().substring(0, 8);
        testInstanceIds.add(instanceId);
        
        CreateInstanceRequest request = new CreateInstanceRequest(
            instanceId,
            defId,
            "Lifecycle Instance",
            null,
            null,
            null,
            null,
            null,
            null
        );
        
        pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        
        // Start instance
        ValidationResult startResult = pipelineInstanceService.startInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertTrue(startResult.valid());
        
        // Verify status
        PipelineInstance running = pipelineInstanceService.getInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertEquals(PipelineInstance.PipelineInstanceStatus.RUNNING, running.status());
        assertNotNull(running.startedAt());
        
        // Try to start again
        ValidationResult startAgain = pipelineInstanceService.startInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertFalse(startAgain.valid());
        assertTrue(startAgain.errors().stream()
            .anyMatch(e -> e.contains("already running")));
        
        // Stop instance
        ValidationResult stopResult = pipelineInstanceService.stopInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertTrue(stopResult.valid());
        
        // Verify stopped
        PipelineInstance stopped = pipelineInstanceService.getInstance(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertEquals(PipelineInstance.PipelineInstanceStatus.STOPPED, stopped.status());
        assertNotNull(stopped.stoppedAt());
    }
    
    @Test
    void testInstanceExists() {
        // Create instance
        String defId = createTestDefinition("exists-pipeline", "Exists Test Pipeline");
        String instanceId = "exists-instance-" + UUID.randomUUID().toString().substring(0, 8);
        testInstanceIds.add(instanceId);
        
        // Check before creation
        boolean existsBefore = pipelineInstanceService.instanceExists(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertFalse(existsBefore);
        
        // Create instance
        CreateInstanceRequest request = new CreateInstanceRequest(
            instanceId,
            defId,
            "Exists Instance",
            null,
            null,
            null,
            null,
            null,
            null
        );
        
        pipelineInstanceService.createInstance(testClusterName, request)
            .await().atMost(Duration.ofSeconds(5));
        
        // Check after creation
        boolean existsAfter = pipelineInstanceService.instanceExists(testClusterName, instanceId)
            .await().atMost(Duration.ofSeconds(5));
        
        assertTrue(existsAfter);
    }
    
    @Test
    void testListInstancesByDefinition() {
        // Create two pipeline definitions
        String defId1 = createTestDefinition("def1-pipeline", "Definition 1");
        String defId2 = createTestDefinition("def2-pipeline", "Definition 2");
        
        // Create instances for both definitions
        for (int i = 0; i < 2; i++) {
            String instanceId1 = "def1-instance-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            testInstanceIds.add(instanceId1);
            
            CreateInstanceRequest request1 = new CreateInstanceRequest(
                instanceId1,
                defId1,
                "Def1 Instance " + i,
                null,
                null,
                null,
                null,
                null,
                null
            );
            
            pipelineInstanceService.createInstance(testClusterName, request1)
                .await().atMost(Duration.ofSeconds(5));
            
            String instanceId2 = "def2-instance-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            testInstanceIds.add(instanceId2);
            
            CreateInstanceRequest request2 = new CreateInstanceRequest(
                instanceId2,
                defId2,
                "Def2 Instance " + i,
                null,
                null,
                null,
                null,
                null,
                null
            );
            
            pipelineInstanceService.createInstance(testClusterName, request2)
                .await().atMost(Duration.ofSeconds(5));
        }
        
        // List instances by definition
        List<PipelineInstance> def1Instances = pipelineInstanceService.listInstancesByDefinition(defId1)
            .await().atMost(Duration.ofSeconds(5));
        
        assertEquals(2, def1Instances.size());
        def1Instances.forEach(instance -> {
            assertEquals(defId1, instance.pipelineDefinitionId());
            assertTrue(instance.instanceId().startsWith("def1-instance-"));
        });
        
        List<PipelineInstance> def2Instances = pipelineInstanceService.listInstancesByDefinition(defId2)
            .await().atMost(Duration.ofSeconds(5));
        
        assertEquals(2, def2Instances.size());
        def2Instances.forEach(instance -> {
            assertEquals(defId2, instance.pipelineDefinitionId());
            assertTrue(instance.instanceId().startsWith("def2-instance-"));
        });
    }
    
    /**
     * Helper method to create a test pipeline definition
     */
    protected String createTestDefinition(String id, String name) {
        String defId = id + "-" + UUID.randomUUID().toString().substring(0, 8);
        testDefinitionIds.add(defId);
        
        PipelineConfig definition = new PipelineConfig(
            name,
            Map.of()
        );
        
        // Store the definition
        pipelineDefinitionService.createDefinition(defId, definition)
            .await().atMost(Duration.ofSeconds(5));
        
        return defId;
    }
}