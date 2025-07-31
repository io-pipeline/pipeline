package io.pipeline.module.opensearchsink;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.SemanticChunk;
import io.pipeline.data.model.SemanticProcessingResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service responsible for converting PipeDoc protobuf messages into OpenSearch BulkOperation objects.
 * This is a pure data transformation component.
 */
@ApplicationScoped
public class DocumentConverterService {

    public List<BulkOperation> prepareBulkOperations(PipeDoc document, String indexName) {
        List<BulkOperation> operations = new ArrayList<>();

        for (SemanticProcessingResult result : document.getSemanticResultsList()) {
            if (result.getChunksCount() > 0) {
                for (SemanticChunk chunk : result.getChunksList()) {
                    if (!chunk.hasEmbeddingInfo() || chunk.getEmbeddingInfo().getVectorCount() == 0) {
                        continue; // Skip chunks without embeddings
                    }

                    Map<String, Object> chunkDoc = new HashMap<>();
                    chunkDoc.put("document_id", document.getId());
                    chunkDoc.put("chunk_id", chunk.getChunkId());
                    chunkDoc.put("chunk_text", chunk.getEmbeddingInfo().getTextContent());
                    chunkDoc.put("embedding", chunk.getEmbeddingInfo().getVectorList());

                    // Add more metadata and fields as needed...

                    IndexOperation<Object> indexOp = new IndexOperation.Builder<>()
                            .index(indexName)
                            .id(chunk.getChunkId())
                            .document(chunkDoc)
                            .build();

                    operations.add(new BulkOperation.Builder().index(indexOp).build());
                }
            }
        }
        return operations;
    }
}
