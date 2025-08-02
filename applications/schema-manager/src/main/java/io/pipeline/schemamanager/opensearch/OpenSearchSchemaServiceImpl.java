package io.pipeline.schemamanager.opensearch;

import io.pipeline.schemamanager.v1.VectorFieldDefinition;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.GetMappingResponse;

@ApplicationScoped
public class OpenSearchSchemaServiceImpl implements OpenSearchSchemaService {

    @Inject
    OpenSearchClient client;

    @Override
    public Uni<Boolean> nestedMappingExists(String indexName, String nestedFieldName) {
        // Use the .async() method to get the asynchronous client API
        return Uni.createFrom().completionStage(
            client.async().indices().getMapping(b -> b.index(indexName))
                .thenApply(this::mappingContainsNestedField)
                .exceptionally(ex -> {
                    // If the index doesn't exist, the future completes exceptionally.
                    // We treat this as "mapping does not exist" and return false.
                    return false;
                })
        );
    }

    private boolean mappingContainsNestedField(GetMappingResponse mapping) {
        // A real implementation would traverse the mapping structure more robustly.
        return mapping.result().values().stream()
                .anyMatch(indexMapping -> indexMapping.mappings().properties().containsKey("embeddings"));
    }

    @Override
    public Uni<Boolean> createIndexWithNestedMapping(String indexName, String nestedFieldName, VectorFieldDefinition vectorFieldDefinition) {
        // TODO: Implement the actual index creation logic with the correct mapping.
        return Uni.createFrom().item(true);
    }
}
