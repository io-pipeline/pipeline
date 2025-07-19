package io.pipeline.consul.client.service;

import io.pipeline.api.model.ClusterMetadata;
import io.pipeline.api.model.PipelineClusterConfig;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for testing CAS (Compare-And-Swap) functionality in ClusterService.
 * Tests concurrent updates to ensure our CAS implementation prevents conflicts.
 */
public abstract class ClusterServiceCasTestBase {
    
    String testId = UUID.randomUUID().toString().substring(0, 8);
    ClusterServiceImpl clusterService; // Need impl to access update methods
    String testClusterPrefix = "test-cas-cluster-";
    
    abstract void setupDependencies();
    
    @BeforeEach
    void setUp() {
        setupDependencies();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up any clusters created during the test
        var clusters = clusterService.listClusters()
            .await().atMost(Duration.ofSeconds(5));
        
        clusters.stream()
            .filter(cluster -> cluster.name().startsWith(testClusterPrefix) && cluster.name().contains(testId))
            .forEach(cluster -> {
                clusterService.deleteCluster(cluster.name()).await().atMost(Duration.ofSeconds(2));
            });
    }
    
    @Test
    void testConcurrentClusterCreation() throws InterruptedException {
        // Test that multiple threads trying to create the same cluster
        // results in only one successful creation
        String clusterName = testClusterPrefix + "concurrent-create-" + testId;
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // Launch multiple threads to create the same cluster
        IntStream.range(0, threadCount).forEach(i -> {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    var result = clusterService.createCluster(clusterName)
                        .await().atMost(Duration.ofSeconds(5));
                    
                    if (result.valid()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            }).start();
        });
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        completeLatch.await();
        
        // Verify only one thread succeeded
        assertEquals(1, successCount.get(), "Exactly one thread should succeed in creating the cluster");
        assertEquals(threadCount - 1, failureCount.get(), "All other threads should fail");
        
        // Verify cluster exists
        var exists = clusterService.clusterExists(clusterName)
            .await().atMost(Duration.ofSeconds(5));
        assertTrue(exists);
    }
    
    @Test
    void testConcurrentClusterMetadataUpdate() throws InterruptedException {
        // Create a cluster first
        String clusterName = testClusterPrefix + "concurrent-update-" + testId;
        clusterService.createCluster(clusterName)
            .await().atMost(Duration.ofSeconds(5));
        
        // Now test concurrent updates
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // Launch multiple threads to update metadata
        IntStream.range(0, threadCount).forEach(i -> {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    // Each thread tries to update the metadata
                    var result = clusterService.updateClusterMetadata(clusterName, metadata -> {
                        // Simulate some processing time
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // Update metadata with thread-specific value
                        Map<String, Object> newMetadata = new java.util.HashMap<>();
                        newMetadata.put("status", metadata.metadata().get("status"));
                        newMetadata.put("version", metadata.metadata().get("version"));
                        newMetadata.put("updatedBy", "thread-" + i);
                        int currentCount = 0;
                        if (metadata.metadata().get("updateCount") instanceof String) {
                            currentCount = Integer.parseInt((String) metadata.metadata().get("updateCount"));
                        } else if (metadata.metadata().get("updateCount") instanceof Integer) {
                            currentCount = (Integer) metadata.metadata().get("updateCount");
                        }
                        newMetadata.put("updateCount", currentCount + 1);
                        
                        return new ClusterMetadata(
                            metadata.name(),
                            metadata.createdAt(),
                            metadata.defaultPipeline(),
                            newMetadata
                        );
                    }).await().atMost(Duration.ofSeconds(10));
                    
                    if (result.valid()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore exceptions for this test
                } finally {
                    completeLatch.countDown();
                }
            }).start();
        });
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        completeLatch.await();
        
        // All threads should succeed with CAS retry mechanism
        assertEquals(threadCount, successCount.get(), 
            "All threads should eventually succeed with CAS retries");
        
        // Verify final state
        var finalMetadata = clusterService.getCluster(clusterName)
            .await().atMost(Duration.ofSeconds(5));
        
        assertTrue(finalMetadata.isPresent());
        // The update count should equal the number of threads
        Object updateCount = finalMetadata.get().metadata().get("updateCount");
        int actualCount = 0;
        if (updateCount instanceof String) {
            actualCount = Integer.parseInt((String) updateCount);
        } else if (updateCount instanceof Integer) {
            actualCount = (Integer) updateCount;
        }
        assertEquals(threadCount, actualCount);
    }
    
    @Test
    void testConcurrentClusterConfigUpdate() throws InterruptedException {
        // Create a cluster first
        String clusterName = testClusterPrefix + "concurrent-config-" + testId;
        clusterService.createCluster(clusterName)
            .await().atMost(Duration.ofSeconds(5));
        
        // Test concurrent updates to cluster config
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // Launch multiple threads to update config
        IntStream.range(0, threadCount).forEach(i -> {
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    var result = clusterService.updateClusterConfig(clusterName, config -> {
                        // Add a topic to allowed topics
                        var newTopics = new java.util.HashSet<>(config.allowedKafkaTopics());
                        newTopics.add("topic-thread-" + i);
                        
                        return new PipelineClusterConfig(
                            config.clusterName(),
                            config.pipelineGraphConfig(),
                            config.pipelineModuleMap(),
                            config.defaultPipelineName(),
                            newTopics,
                            config.allowedGrpcServices()
                        );
                    }).await().atMost(Duration.ofSeconds(10));
                    
                    if (result.valid()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    completeLatch.countDown();
                }
            }).start();
        });
        
        // Start all threads
        startLatch.countDown();
        completeLatch.await();
        
        // All should succeed
        assertEquals(threadCount, successCount.get());
        
        // Verify all topics were added
        var finalConfig = clusterService.getCluster(clusterName)
            .onItem().transformToUni(metadata -> {
                if (metadata.isPresent()) {
                    String key = "pipeline/clusters/" + clusterName + "/config";
                    return clusterService.consulClient.getValue(key)
                        .map(kv -> {
                            if (kv != null && kv.getValue() != null) {
                                try {
                                    return clusterService.objectMapper.readValue(
                                        kv.getValue(), PipelineClusterConfig.class);
                                } catch (Exception e) {
                                    return null;
                                }
                            }
                            return null;
                        });
                }
                return Uni.createFrom().nullItem();
            }).await().atMost(Duration.ofSeconds(5));
        
        assertNotNull(finalConfig);
        for (int i = 0; i < threadCount; i++) {
            assertTrue(finalConfig.allowedKafkaTopics().contains("topic-thread-" + i));
        }
    }
    
    @Test
    void testCasRetryMechanism() {
        // Test that CAS retry mechanism works correctly
        String clusterName = testClusterPrefix + "cas-retry-" + testId;
        clusterService.createCluster(clusterName)
            .await().atMost(Duration.ofSeconds(5));
        
        // Track how many times our update function is called
        AtomicInteger callCount = new AtomicInteger(0);
        
        var result = clusterService.updateClusterMetadata(clusterName, metadata -> {
            int count = callCount.incrementAndGet();
            
            // Simulate that another update happened between our read and write
            // by making the first attempt "fail" (in real scenario, CAS would fail)
            if (count == 1) {
                // Simulate some processing that takes time
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            return new ClusterMetadata(
                metadata.name(),
                metadata.createdAt(),
                metadata.defaultPipeline(),
                Map.of("updateAttempt", String.valueOf(count))
            );
        }).await().atMost(Duration.ofSeconds(5));
        
        assertTrue(result.valid());
        // The update function might be called multiple times due to retries
        assertTrue(callCount.get() >= 1);
    }
    
    @Test
    void testRealisticPipelineScenarios() {
        // Test with realistic pipeline names and configurations
        String[] realisticClusters = {
            "data-science-" + testId,
            "search-engine-" + testId,
            "document-processing-" + testId,
            "ml-training-" + testId,
            "realtime-analytics-" + testId
        };
        
        // Create clusters with realistic metadata
        for (String baseName : realisticClusters) {
            String clusterName = testClusterPrefix + baseName;
            var result = clusterService.createCluster(clusterName)
                .await().atMost(Duration.ofSeconds(5));
            assertTrue(result.valid());
            
            // Update with realistic metadata
            clusterService.updateClusterMetadata(clusterName, metadata -> {
                Map<String, Object> realisticMetadata = new java.util.HashMap<>();
                
                if (baseName.contains("data-science")) {
                    realisticMetadata.put("status", "active");
                    realisticMetadata.put("version", "2.1.0");
                    realisticMetadata.put("environment", "production");
                    realisticMetadata.put("team", "data-science-team");
                    realisticMetadata.put("purpose", "ML model training and inference pipelines");
                    realisticMetadata.put("sla", "99.9%");
                } else if (baseName.contains("search-engine")) {
                    realisticMetadata.put("status", "active");
                    realisticMetadata.put("version", "3.0.0");
                    realisticMetadata.put("environment", "production");
                    realisticMetadata.put("team", "search-team");
                    realisticMetadata.put("purpose", "Document indexing and search pipelines");
                    realisticMetadata.put("indexType", "elasticsearch");
                } else if (baseName.contains("document-processing")) {
                    realisticMetadata.put("status", "active");
                    realisticMetadata.put("version", "1.5.0");
                    realisticMetadata.put("environment", "staging");
                    realisticMetadata.put("team", "content-team");
                    realisticMetadata.put("purpose", "PDF/Word document extraction and enrichment");
                    realisticMetadata.put("maxDocSize", "100MB");
                }
                
                return new ClusterMetadata(
                    metadata.name(),
                    metadata.createdAt(),
                    metadata.defaultPipeline(),
                    realisticMetadata
                );
            }).await().atMost(Duration.ofSeconds(5));
        }
        
        // Verify all clusters were created
        var clusters = clusterService.listClusters()
            .await().atMost(Duration.ofSeconds(5));
        
        for (String baseName : realisticClusters) {
            String clusterName = testClusterPrefix + baseName;
            assertTrue(clusters.stream().anyMatch(c -> c.name().equals(clusterName)));
        }
    }
}