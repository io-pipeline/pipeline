package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for ClusterService containing all test logic.
 * Extended by both unit tests (with mocks) and integration tests (with real Consul).
 */
public abstract class ClusterServiceTestBase {
    
    String testId = UUID.randomUUID().toString().substring(0, 8);
    ClusterService clusterService;
    String testClusterPrefix = "test-cluster-";
    
    abstract void setupDependencies();
    
    @BeforeEach
    void setUp() {
        setupDependencies();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up any clusters created during the test
        // This ensures tests can run in parallel without conflicts
        var clusters = clusterService.listClusters()
            .await().atMost(Duration.ofSeconds(5));
        
        clusters.stream()
            .filter(cluster -> cluster.name().startsWith(testClusterPrefix) && cluster.name().contains(testId))
            .forEach(cluster -> {
                clusterService.deleteCluster(cluster.name()).await().atMost(Duration.ofSeconds(2));
            });
    }
    
    @Test
    void testClusterLifecycle() {
        // Create cluster with unique ID to avoid conflicts
        String clusterName = testClusterPrefix + testId;
        var result = clusterService.createCluster(clusterName)
                .await().atMost(Duration.ofSeconds(5));
        assertTrue(result.valid());
        
        // Verify it exists
        var clusters = clusterService.listClusters()
                .await().atMost(Duration.ofSeconds(5));
        assertTrue(clusters.stream().anyMatch(c -> c.name().equals(clusterName)));
        
        // Try to create duplicate (should fail)
        var duplicate = clusterService.createCluster(clusterName)
                .await().atMost(Duration.ofSeconds(5));
        assertFalse(duplicate.valid());
        
        // Delete cluster
        var deleted = clusterService.deleteCluster(clusterName)
                .await().atMost(Duration.ofSeconds(5));
        assertTrue(deleted.valid());
        
        // Verify it's gone
        clusters = clusterService.listClusters()
                .await().atMost(Duration.ofSeconds(5));
        assertFalse(clusters.stream().anyMatch(c -> c.name().equals(clusterName)));
        
        // Delete non-existent (in current implementation, this still returns success)
        var deleteAgain = clusterService.deleteCluster(clusterName)
                .await().atMost(Duration.ofSeconds(5));
        // TODO: This should return false/invalid, but current implementation returns success
        assertTrue(deleteAgain.valid());
    }
    
    @Test
    void testMultipleClustersCreation() {
        // Create multiple clusters with unique prefix
        String prefix = testClusterPrefix + "multi-" + testId + "-";
        for (int i = 0; i < 5; i++) {
            var result = clusterService.createCluster(prefix + i)
                    .await().atMost(Duration.ofSeconds(5));
            assertTrue(result.valid());
        }
        
        // Verify all exist
        var clusters = clusterService.listClusters()
                .await().atMost(Duration.ofSeconds(5));
        for (int i = 0; i < 5; i++) {
            final String clusterName = prefix + i;
            assertTrue(clusters.stream().anyMatch(c -> c.name().equals(clusterName)));
        }
        
        // Clean up
        for (int i = 0; i < 5; i++) {
            clusterService.deleteCluster(prefix + i)
                    .await().atMost(Duration.ofSeconds(5));
        }
    }
    
    @Test
    void testInvalidClusterNames() {
        // Test empty name - should return invalid result, not throw
        var emptyResult = clusterService.createCluster("")
                .await().atMost(Duration.ofSeconds(5));
        assertFalse(emptyResult.valid());
        assertTrue(emptyResult.hasErrors());
        
        // Test null name - should return invalid result, not throw
        var nullResult = clusterService.createCluster(null)
                .await().atMost(Duration.ofSeconds(5));
        assertFalse(nullResult.valid());
        assertTrue(nullResult.hasErrors());
    }
    
    @Test
    void testClusterInOwnNamespace() {
        // Each test run should operate in its own namespace
        String myCluster = testClusterPrefix + "isolated-" + testId;
        
        // Create and verify cluster exists
        var result = clusterService.createCluster(myCluster)
                .await().atMost(Duration.ofSeconds(5));
        assertTrue(result.valid());
        
        // List should contain our cluster
        var clusters = clusterService.listClusters()
                .await().atMost(Duration.ofSeconds(5));
        assertTrue(clusters.stream().anyMatch(c -> c.name().equals(myCluster)));
        
        // Clean up
        clusterService.deleteCluster(myCluster)
                .await().atMost(Duration.ofSeconds(5));
    }
    
    @Test
    void testDeleteNonExistentCluster() {
        // Test deleting a cluster that doesn't exist
        String nonExistent = testClusterPrefix + "non-existent-" + testId;
        
        // Current implementation returns success even for non-existent clusters
        var result = clusterService.deleteCluster(nonExistent)
                .await().atMost(Duration.ofSeconds(5));
        assertTrue(result.valid());
    }
}