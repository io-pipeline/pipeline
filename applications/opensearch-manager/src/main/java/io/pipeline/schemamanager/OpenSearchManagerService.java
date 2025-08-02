package io.pipeline.schemamanager;

import io.pipeline.schemamanager.opensearch.OpenSearchSchemaService;
import io.pipeline.opensearch.v1.*;
import io.quarkus.grpc.GrpcService;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@GrpcService
public class OpenSearchManagerService extends MutinyOpenSearchManagerServiceGrpc.OpenSearchManagerServiceImplBase {

    private static final Logger LOG = Logger.getLogger(OpenSearchManagerService.class);

    @Inject
    ReactiveRedisDataSource redis;

    @Inject
    OpenSearchSchemaService openSearchClient; // Inject the interface

    private static final String SERVICE_NAMESPACE = "schema-manager";
    private static final String CACHE_KEY_PREFIX = "cache:mapping:";
    private static final String LOCK_KEY_PREFIX = "lock:mapping:";

    @ConfigProperty(name = "schema.manager.lock.timeout", defaultValue = "PT10S")
    Duration lockTimeout;

    private ReactiveValueCommands<String, String> stringCommands() {
        return redis.value(String.class, String.class);
    }

    private String getCacheKey(String indexName) {
        return SERVICE_NAMESPACE + ":" + CACHE_KEY_PREFIX + indexName;
    }

    private String getLockKey(String indexName) {
        return SERVICE_NAMESPACE + ":" + LOCK_KEY_PREFIX + indexName;
    }

    @Override
    public Uni<EnsureNestedEmbeddingsFieldExistsResponse> ensureNestedEmbeddingsFieldExists(EnsureNestedEmbeddingsFieldExistsRequest request) {
        final String indexName = request.getIndexName();
        final String cacheKey = getCacheKey(indexName);
        final String lockKey = getLockKey(indexName);
        final String lockValue = UUID.randomUUID().toString();

        // 1. Check cache
        return stringCommands().get(cacheKey)
                .onItem().transformToUni(cachedSchemaVersion -> {
                    if (cachedSchemaVersion != null) {
                        return Uni.createFrom().item(buildResponse(true));
                    }
                    // 2. Cache Miss: Acquire lock
                    return acquireLock(lockKey, lockValue)
                            .onItem().transformToUni(lockAcquired -> {
                                if (!lockAcquired) {
                                    throw new RuntimeException("Could not acquire lock for schema update on index: " + indexName);
                                }
                                // 3. Lock acquired: Manage schema and release lock
                                return manageSchema(request, cacheKey)
                                        .eventually(() -> releaseLock(lockKey, lockValue));
                            });
                });
    }

    private Uni<Boolean> acquireLock(String lockKey, String lockValue) {
        // Atomic set-if-not-exists with an expiration
        return stringCommands().setnx(lockKey, lockValue)
                .onItem().transformToUni(acquired -> {
                    if (acquired) {
                        // If we acquired the lock, we must set the expiration
                        return redis.key(String.class).expire(lockKey, lockTimeout).replaceWith(true);
                    }
                    return Uni.createFrom().item(false);
                });
    }

    private Uni<Void> releaseLock(String lockKey, String lockValue) {
        // Safely release the lock only if we still own it (compare-and-delete)
        return stringCommands().get(lockKey)
                .onItem().transformToUni(currentLockValue -> {
                    if (lockValue.equals(currentLockValue)) {
                        return redis.key(String.class).del(lockKey).replaceWithVoid();
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    private Uni<EnsureNestedEmbeddingsFieldExistsResponse> manageSchema(EnsureNestedEmbeddingsFieldExistsRequest request, String cacheKey) {
        // Double-check cache now that we have the lock
        return stringCommands().get(cacheKey)
                .onItem().transformToUni(cachedSchemaVersion -> {
                    if (cachedSchemaVersion != null) {
                        return Uni.createFrom().item(buildResponse(true));
                    }
                    // Still a cache miss, now we talk to the source of truth via our interface
                    return openSearchClient.nestedMappingExists(request.getIndexName(), request.getNestedFieldName())
                            .onItem().transformToUni(exists -> {
                                if (exists) {
                                    LOG.infof("Schema already exists in OpenSearch for index '%s'. Updating cache.", request.getIndexName());
                                    return stringCommands().set(cacheKey, "v1").replaceWith(buildResponse(true));
                                } else {
                                    LOG.infof("Schema does not exist for index '%s'. Creating it now.", request.getIndexName());
                                    return openSearchClient.createIndexWithNestedMapping(request.getIndexName(), request.getNestedFieldName(), request.getVectorFieldDefinition())
                                            .onItem().transformToUni(success -> {
                                                if (success) {
                                                    LOG.infof("Successfully created schema for index '%s'. Updating cache.", request.getIndexName());
                                                    return stringCommands().set(cacheKey, "v1").replaceWith(buildResponse(false));
                                                } else {
                                                    throw new RuntimeException("Failed to create schema for index: " + request.getIndexName());
                                                }
                                            });
                                }
                            });
                });
    }

    private EnsureNestedEmbeddingsFieldExistsResponse buildResponse(boolean existed) {
        return EnsureNestedEmbeddingsFieldExistsResponse.newBuilder().setSchemaExisted(existed).build();
    }
}
