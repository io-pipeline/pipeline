package com.rokkon.pipeline.consul;

import io.smallrye.mutiny.groups.UniAwait;
import io.vertx.mutiny.ext.consul.ConsulClient;
import io.vertx.ext.consul.KeyValue;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Arrays; // Added import

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


public abstract class ParallelConsulKvTestBase {
    private static final Logger LOG = Logger.getLogger(ParallelConsulKvTestBase.class);

    protected abstract ConsulClient getConsulClient();

    protected String testId;
    protected String testKvPrefix;

    @BeforeEach
    void setupBase() {
        testId = UUID.randomUUID().toString().substring(0, 8);
        testKvPrefix = "test-kv/" + testId;
        LOG.infof("Test setup with ID: %s, prefix: %s", testId, testKvPrefix);
    }

    @Test
    void testParallelKvWrites() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);

        try {
            Uni<Void>[] unis = new Uni[10];
            for (int i = 0; i < 10; i++) {
                final int index = i;
                String key = testKvPrefix + "/concurrent/thread-" + index;
                String value = "value-from-thread-" + index;
                unis[i] = getConsulClient().putValue(key, value).replaceWithVoid().runSubscriptionOn(executor);
            }

            // Fix 2: Convert array to list
            Uni.join().all(Arrays.asList(unis)).andFailFast().await().indefinitely();

            for (int i = 0; i < 10; i++) {
                String key = testKvPrefix + "/concurrent/thread-" + i;
                // Fix 1: Check for null directly
                KeyValue kv = getConsulClient().getValue(key).await().indefinitely();
                assertNotNull(kv);
                assertEquals("value-from-thread-" + i, kv.getValue());
            }
            LOG.infof("✓ Concurrent writes within test %s successful", testId);
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testNoInterferenceAcrossTests() {
        String myKey = testKvPrefix + "/isolation/verified";
        getConsulClient().putValue(myKey, "true").await().indefinitely();

        String otherTestKey = "test-kv/other-test-id/config/app";
        // Fix 1: Check for null directly, and remove Optional wrapper
        KeyValue otherResult = getConsulClient().getValue(otherTestKey).await().indefinitely();
        assertNull(otherResult); // Should be null if empty

        // Fix 1: Check for null directly, and remove Optional wrapper
        KeyValue myResult = getConsulClient().getValue(myKey).await().indefinitely();
        assertNotNull(myResult);
        assertEquals("true", myResult.getValue());
        LOG.infof("✓ Test isolation verified - no interference from other tests");
    }

    @Test
    void testServiceIsolation() {
        String serviceKey = testKvPrefix + "/services/parallel-test-service/config";
        String serviceConfig = "service:\n  timeout: 30s\n  retries: 3\n";

        getConsulClient().putValue(serviceKey, serviceConfig).await().indefinitely();

        UniAwait<KeyValue> result = getConsulClient().getValue(serviceKey).await();
        assertNotNull(result);
        assertEquals(serviceConfig, result.indefinitely().getValue());
        LOG.infof("✓ Service config stored in isolated namespace: %s", testKvPrefix);
    }

    @Test
    void testCleanupDoesNotAffectOtherTests() {
        String key1 = testKvPrefix + "/cleanup/test1";
        String key2 = testKvPrefix + "/cleanup/test2";

        getConsulClient().putValue(key1, "data1").await().indefinitely();
        getConsulClient().putValue(key2, "data2").await().indefinitely();

        getConsulClient().deleteValue(key1).await().indefinitely();

        // Fix 1: Check for null directly
        KeyValue result1 = getConsulClient().getValue(key1).await().indefinitely();
        KeyValue result2 = getConsulClient().getValue(key2).await().indefinitely();

        assertNull(result1); // Should be null after delete
        assertNotNull(result2); // Should still exist
        assertEquals("data2", result2.getValue());
        LOG.infof("✓ Cleanup isolation verified");
    }

    @Test
    void testConcurrentWritesWithinTest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);

        try {
            Uni<Void>[] unis = new Uni[10];
            for (int i = 0; i < 10; i++) {
                final int index = i;
                unis[i] = getConsulClient().putValue(testKvPrefix + "/concurrent/thread-" + index, "value-from-thread-" + index).replaceWithVoid().runSubscriptionOn(executor);
            }

            // Fix 2: Convert array to list
            Uni.join().all(Arrays.asList(unis)).andFailFast().await().indefinitely();
            LOG.infof("✓ Concurrent writes within test %s successful", testId);
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}