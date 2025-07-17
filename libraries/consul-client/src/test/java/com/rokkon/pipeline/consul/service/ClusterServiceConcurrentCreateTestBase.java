package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.service.ClusterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract base test class for testing concurrent cluster creation with CAS.
 * This class contains the test logic but no dependency injection.
 * Subclasses must implement setupService() to provide the ClusterService instance.
 */
public abstract class ClusterServiceConcurrentCreateTestBase {
    
    protected ClusterService clusterService;
    protected String testId = UUID.randomUUID().toString().substring(0, 8);
    protected String testClusterPrefix = "test-concurrent-create-";
    
    /**
     * Abstract method that subclasses must implement to set up the ClusterService.
     * Unit tests will provide a mocked service, integration tests will provide a real one.
     */
    protected abstract void setupService();
    
    @BeforeEach
    void setUp() {
        setupService();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up any clusters created during the test
        if (clusterService != null) {
            var clusters = clusterService.listClusters()
                .await().atMost(Duration.ofSeconds(5));
            
            clusters.stream()
                .filter(cluster -> cluster.name().startsWith(testClusterPrefix) && cluster.name().contains(testId))
                .forEach(cluster -> {
                    clusterService.deleteCluster(cluster.name()).await().atMost(Duration.ofSeconds(2));
                });
        }
    }
    
    @Test
    void testConcurrentClusterCreation() {
        // Test that multiple threads trying to create the same cluster
        // results in only one successful creation
        String clusterName = testClusterPrefix + testId;
        int threadCount = 5;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger threadsStarted = new AtomicInteger(0);
        
        // Launch multiple threads to create the same cluster
        IntStream.range(0, threadCount).forEach(i -> {
            new Thread(() -> {
                threadsStarted.incrementAndGet();
                
                // Wait for all threads to be ready
                Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> threadsStarted.get() == threadCount);
                
                try {
                    var result = clusterService.createCluster(clusterName)
                        .await().atMost(Duration.ofSeconds(5));
                    
                    if (result.valid()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }).start();
        });
        
        // Wait for all threads to complete
        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> (successCount.get() + failureCount.get()) == threadCount);
        
        // Verify only one thread succeeded
        assertEquals(1, successCount.get(), "Exactly one thread should succeed in creating the cluster");
        assertEquals(threadCount - 1, failureCount.get(), "All other threads should fail");
        
        // Verify cluster exists
        var exists = clusterService.clusterExists(clusterName)
            .await().atMost(Duration.ofSeconds(5));
        assertTrue(exists);
    }
}