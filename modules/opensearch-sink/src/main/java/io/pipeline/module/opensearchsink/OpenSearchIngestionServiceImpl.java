package io.pipeline.module.opensearchsink;

import io.pipeline.ingestion.proto.IngestionRequest;
import io.pipeline.ingestion.proto.IngestionResponse;
import io.pipeline.ingestion.proto.MutinyOpenSearchIngestionGrpc;
import io.pipeline.module.opensearchsink.service.DocumentConverterService;
import io.pipeline.module.opensearchsink.service.OpenSearchRepository;
import io.pipeline.module.opensearchsink.SchemaManagerService;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@GrpcService
public class OpenSearchIngestionServiceImpl extends MutinyOpenSearchIngestionGrpc.OpenSearchIngestionImplBase {

    private static final Logger LOG = Logger.getLogger(OpenSearchIngestionServiceImpl.class);

    private final SchemaManagerService schemaManager;
    private final DocumentConverterService documentConverter;
    private final OpenSearchRepository openSearchRepository;

    @Inject
    public OpenSearchIngestionServiceImpl(
            SchemaManagerService schemaManager,
            DocumentConverterService documentConverter,
            OpenSearchRepository openSearchRepository) {
        this.schemaManager = schemaManager;
        this.documentConverter = documentConverter;
        this.openSearchRepository = openSearchRepository;
    }

    @Override
    public Multi<IngestionResponse> streamDocuments(Multi<IngestionRequest> requestStream) {
        // For now, we process each request individually.
        // We will add buffering logic later based on the configuration.
        return requestStream.onItem().transformToUniAndMerge(this::processSingleRequest);
    }

    private Uni<IngestionResponse> processSingleRequest(IngestionRequest request) {
        if (!request.hasDocument()) {
            return Uni.createFrom().item(buildResponse(request, false, "IngestionRequest has no document."));
        }

        String indexName = schemaManager.determineIndexName(request.getDocument().getDocumentType());

        return schemaManager.ensureIndexExists(indexName)
                .onItem().transform(v -> documentConverter.prepareBulkOperations(request.getDocument(), indexName))
                .onItem().transformToUni(openSearchRepository::bulk)
                .onItem().transform(bulkResponse -> {
                    if (bulkResponse.errors()) {
                        LOG.warnf("Bulk request had errors for document %s", request.getDocument().getId());
                        return buildResponse(request, false, "Bulk operation completed with errors.");
                    } else {
                        LOG.infof("Successfully indexed document %s", request.getDocument().getId());
                        return buildResponse(request, true, "Document indexed successfully.");
                    }
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Failed to process document %s", request.getDocument().getId());
                    return buildResponse(request, false, "Processing failed: " + error.getMessage());
                });
    }

    private IngestionResponse buildResponse(IngestionRequest request, boolean success, String message) {
        String docId = request.hasDocument() ? request.getDocument().getId() : "";
        return IngestionResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setDocumentId(docId)
                .setSuccess(success)
                .setMessage(message)
                .build();
    }
}