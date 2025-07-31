package io.pipeline.module.opensearchsink;

import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;

import java.time.Duration;
import java.util.List;
import java.util.Random;

@ApplicationScoped
public class SchemaManagerService {

    private static final Logger LOG = Logger.getLogger(SchemaManagerService.class);
    private static final String LOCK_PREFIX = "schema-lock:";
    private static final String LOCK_VALUE = "locked";
    private static final int LOCK_TIMEOUT_MS = 30000;
    private static final String META_INDEX_NAME = ".pipeline_index_schemas";

    private final OpenSearchAsyncClient osClient;
    private final ReactiveRedisClient redisClient;
    private final Random random = new Random();

    @ConfigProperty(name = "opensearch.default.index-prefix", defaultValue = "pipeline")
    String defaultIndexPrefix;

    @ConfigProperty(name = "opensearch.default.vector-dimension", defaultValue = "384")
    int defaultVectorDimension;

    @Inject
    public SchemaManagerService(OpenSearchAsyncClient osClient, ReactiveRedisClient redisClient) {
        this.osClient = osClient;
        this.redisClient = redisClient;
    }

    public String determineIndexName(String documentType) {
        String baseName = (documentType == null || documentType.isEmpty()) ? "documents" : documentType;
        return defaultIndexPrefix + "-" + baseName.toLowerCase().replaceAll("[^a-z0-9_\\-", "_");
    }

    public Uni<Void> ensureIndexExists(String indexName) {
        String lockKey = LOCK_PREFIX + indexName;

        return tryToAcquireLock(lockKey)
                .flatMap(lockAcquired -> {
                    if (lockAcquired) {
                        LOG.infof("Lock acquired for index: %s", indexName);
                        return doSchemaCheckAndCreation(indexName)
                                .eventually(() -> releaseLock(lockKey));
                    } else {
                        LOG.infof("Could not acquire lock for index: %s, retrying...", indexName);
                        return Uni.createFrom().voidItem()
                                .onItem().delayIt().by(Duration.ofMillis(100 + random.nextInt(150)))
                                .onItem().transformToUni(v -> ensureIndexExists(indexName));
                    }
                });
    }

    private Uni<Boolean> tryToAcquireLock(String key) {
        return redisClient.set(List.of(key, LOCK_VALUE, "NX", "PX", String.valueOf(LOCK_TIMEOUT_MS)))
                .map(response -> "OK".equals(response.toString()));
    }

    private Uni<Void> releaseLock(String key) {
        LOG.infof("Releasing lock for key: %s", key);
        return redisClient.del(List.of(key)).toUni().replaceWithVoid();
    }

    private Uni<Void> doSchemaCheckAndCreation(String indexName) {
        return Uni.createFrom().completionStage(() -> osClient.indices().exists(new ExistsRequest.Builder().index(indexName).build()))
                .flatMap(response -> {
                    if (response.value()) {
                        LOG.infof("Index %s already exists.", indexName);
                        // In a real implementation, you would also check and update the mapping here.
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

        return Uni.createFrom().completionStage(() -> osClient.indices().create(createIndexRequest))
                .onItem().invoke(response -> LOG.infof("Successfully created index %s", indexName))
                .onFailure().invoke(error -> LOG.errorf(error, "Failed to create index %s", indexName))
                .replaceWithVoid();
    }
}