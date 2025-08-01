package io.pipeline.module.opensearchsink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.common.service.SchemaExtractorService;
import io.pipeline.data.module.*;
import io.pipeline.module.opensearchsink.service.DocumentConverterService;
import io.pipeline.module.opensearchsink.service.OpenSearchRepository;
import io.pipeline.module.opensearchsink.config.opensearch.BatchOptions;
import io.pipeline.module.opensearchsink.service.SchemaManagerService;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@GrpcService
@PipelineAutoRegister(moduleType = "sink", useHttpPort = true)
public class OpenSearchSinkServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(OpenSearchSinkServiceImpl.class);

    @Inject
    SchemaExtractorService schemaExtractorService;

    @Inject
    SchemaManagerService schemaManager;

    @Inject
    DocumentConverterService documentConverter;

    @Inject
    OpenSearchRepository openSearchRepository;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        if (!request.hasDocument()) {
            return Uni.createFrom().item(ModuleProcessResponse.newBuilder().setSuccess(true).addProcessorLogs("No document to process.").build());
        }

        BatchOptions options = extractConfiguration(request);

        return schemaManager.ensureSchemaIsReady(request.getDocument(), options)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()) // Offload blocking schema work
                .onItem().transform(indexName -> documentConverter.prepareBulkOperations(request.getDocument(), indexName))
                .onItem().transformToUni(openSearchRepository::bulk)
                .onItem().transform(bulkResponse -> {
                    ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder().setOutputDoc(request.getDocument());
                    if (bulkResponse.errors()) {
                        responseBuilder.setSuccess(false).addProcessorLogs("Bulk operation completed with errors.");
                    } else {
                        responseBuilder.setSuccess(true).addProcessorLogs("Document indexed successfully.");
                    }
                    return responseBuilder.build();
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Failed to process document %s", request.getDocument().getId());
                    return ModuleProcessResponse.newBuilder().setSuccess(false).addProcessorLogs("Failed to process document: " + error.getMessage()).build();
                });
    }

    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        ServiceRegistrationResponse.Builder responseBuilder = ServiceRegistrationResponse.newBuilder()
                .setModuleName("opensearch-sink")
                .setCapabilities(Capabilities.newBuilder().addTypes(CapabilityType.SINK).build());

        schemaExtractorService.extractSchemaForClass(BatchOptions.class).ifPresentOrElse(
                responseBuilder::setJsonConfigSchema,
                () -> LOG.error("Could not extract schema for OpenSearchSinkOptions.class")
        );

        responseBuilder.setHealthCheckPassed(true).setHealthCheckMessage("Service is running.");
        return Uni.createFrom().item(responseBuilder.build());
    }

    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        return processData(request);
    }

    private BatchOptions extractConfiguration(ModuleProcessRequest request) {
        if (request.hasConfig() && request.getConfig().hasCustomJsonConfig()) {
            try {
                Struct jsonConfig = request.getConfig().getCustomJsonConfig();
                String jsonString = JsonFormat.printer().print(jsonConfig);
                return objectMapper.readValue(jsonString, BatchOptions.class);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to parse OpenSearchSinkOptions from JSON, using defaults.");
            }
        }
        return new BatchOptions();
    }
}
