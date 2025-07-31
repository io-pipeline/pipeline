package io.pipeline.module.opensearchsink;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

import java.util.List;

/**
 * A reactive repository for interacting with OpenSearch.
 * This class is a thin wrapper around the OpenSearchAsyncClient to isolate data access logic.
 */
@ApplicationScoped
public class OpenSearchRepository {

    private final OpenSearchAsyncClient asyncClient;

    @Inject
    public OpenSearchRepository(OpenSearchAsyncClient asyncClient) {
        this.asyncClient = asyncClient;
    }

    /**
     * Performs a bulk indexing or deleting operation asynchronously.
     *
     * @param operations The list of bulk operations to perform.
     * @return a Uni that will resolve to the BulkResponse.
     */
    public Uni<BulkResponse> bulk(List<BulkOperation> operations) {
        BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();
        return Uni.createFrom().completionStage(() -> asyncClient.bulk(bulkRequest));
    }
}
