package io.pipeline.module.opensearchsink.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

import java.util.List;

@ApplicationScoped
public class OpenSearchRepository {

    private final OpenSearchAsyncClient asyncClient;

    @Inject
    public OpenSearchRepository(OpenSearchAsyncClient asyncClient) {
        this.asyncClient = asyncClient;
    }

    public Uni<BulkResponse> bulk(List<BulkOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return Uni.createFrom().item(BulkResponse.of(b -> b.items(List.of()).errors(false).took(0)));
        }
        BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();
        return Uni.createFrom().completionStage(() -> asyncClient.bulk(bulkRequest));
    }
}