package io.pipeline.module.opensearchsink;

import io.pipeline.module.opensearchsink.opensearch.ReactiveOpenSearchClient;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;

import java.time.Duration;
import java.util.Random;

@ApplicationScoped
public class SchemaManagerService {

    private static final Logger LOG = Logger.getLogger(SchemaManagerService.class);
    private static final String LOCK_PREFIX = "schema-lock:";
    private static final String LOCK_VALUE = "locked";
    private static final int LOCK_TIMEOUT_MS = 30000;

    private final ReactiveOpenSearchClient osClient;
    private final ReactiveRedisDataSource redis;
    private final Random random = new Random();

    @ConfigProperty(name = "opensearch.default.index-prefix", defaultValue = "pipeline")
    String defaultIndexPrefix;

    @ConfigProperty(name = "opensearch.default.vector-dimension", defaultValue = "384")
    int defaultVectorDimension;

    @Inject
    public SchemaManagerService(ReactiveOpenSearchClient osClient, ReactiveRedisDataSource redis) {
        this.osClient = osClient;
        this.redis = redis;
    }

    public String determineIndexName(String documentType) {
        String baseName = (documentType == null || documentType.isEmpty()) ? "documents" : documentType;
        // Corrected the regex to properly escape the hyphen
        return defaultIndexPrefix + "-" + baseName.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }

    public Uni<Void> ensureIndexExists(String indexName) {
        String lockKey = LOCK_PREFIX + indexName;

        // tryToAcquireLock now returns Uni<Void>.
        // A successful completion (onItem) means the lock was acquired.
        // A failure means it was not, so we recover and retry.
        return tryToAcquireLock(lockKey)
                .onItem().transformToUni(v -> {
                    LOG.infof("Lock acquired for index: %s", indexName);
                    return doSchemaCheckAndCreation(indexName)
                            .eventually(() -> releaseLock(lockKey));
                })
                .onFailure().recoverWithUni(() -> {
                    LOG.infof("Could not acquire lock for index: %s, retrying...", indexName);
                    return Uni.createFrom().voidItem()
                            .onItem().delayIt().by(Duration.ofMillis(100 + random.nextInt(150)))
                            .onItem().transformToUni(v -> ensureIndexExists(indexName));
                });
    }

    /**
     * Tries to acquire a distributed lock.
     * The operation completes successfully (onItem) if the lock is acquired.
     * It fails if the lock is already held (due to the NX argument).
     * @param key The lock key.
     * @return a Uni<Void> that completes if the lock is acquired, fails otherwise.
     */
    private Uni<Void> tryToAcquireLock(String key) {
        return redis.value(String.class).set(key, LOCK_VALUE, new SetArgs().nx().px(LOCK_TIMEOUT_MS));
    }

    private Uni<Void> releaseLock(String key) {
        LOG.infof("Releasing lock for key: %s", key);
        return redis.key(String.class).del(key).replaceWithVoid();
    }

    private Uni<Void> doSchemaCheckAndCreation(String indexName) {
        return osClient.indexExists(indexName)
                .flatMap(exists -> {
                    if (exists) {
                        LOG.infof("Index %s already exists.", indexName);
                        return Uni.createFrom().voidItem();
                    } else {
                        LOG.infof("Index %s does not exist. Creating...", indexName);
                        return createNewIndex(indexName);
                    }
                });
    }

    private Uni<Void> createNewIndex(String indexName) {
        IndexSettings settings = new IndexSettings.Builder().knn(true).build();

        TypeMapping mapping = new TypeMapping.Builder()
                .dynamic(DynamicMapping.True)
                .properties("embedding", p -> p.knnVector(k -> k.dimension(defaultVectorDimension)))
                .build();

        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder()
                .index(indexName)
                .settings(settings)
                .mappings(mapping)
                .build();

        return osClient.createIndex(createIndexRequest)
                .onItem().invoke(response -> LOG.infof("Successfully created index %s", indexName))
                .onFailure().invoke(error -> LOG.errorf(error, "Failed to create index %s", indexName))
                .replaceWithVoid();
    }
}