package io.pipeline.module.chunker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.common.service.SchemaExtractorService;
import io.pipeline.common.util.ProcessingBuffer;
import io.pipeline.data.model.ChunkEmbedding;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.SemanticChunk;
import io.pipeline.data.model.SemanticProcessingResult;
import io.pipeline.data.module.*;
import io.pipeline.module.chunker.config.ChunkerConfig;
import io.pipeline.module.chunker.model.Chunk;
import io.pipeline.module.chunker.model.ChunkerOptions;
import io.pipeline.module.chunker.model.ChunkingResult;
import io.pipeline.module.chunker.service.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chunker gRPC service implementation using Quarkus reactive patterns with Mutiny.
 * This service receives documents through gRPC and processes them by breaking them
 * into smaller, overlapping chunks for further processing.
 */
@Singleton
@GrpcService
@PipelineAutoRegister(
        moduleType = "chunker", // Type identifier for this module
        useHttpPort = true,  // Using unified HTTP/gRPC server on port 39100
        metadata = {"category=nlp", "complexity=medium"} // Additional metadata for discovery
)
public class ChunkerGrpcImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(ChunkerGrpcImpl.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OverlapChunker overlapChunker;

    @Inject
    ChunkMetadataExtractor metadataExtractor;

    @Inject
    ProcessingBuffer<PipeDoc> outputBuffer;

    @Inject
    SchemaExtractorService schemaExtractorService;

    @Override
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        if (request == null) {
            LOG.error("Received null request");
            return Uni.createFrom().item(createErrorResponse("Request cannot be null", null));
        }

        // Use the internal method with isTest=false
        return processDataInternal(request, false);
    }

    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                // Use SchemaExtractorService to get the dynamically generated ChunkerConfig schema
                Optional<String> schemaOptional = schemaExtractorService.extractChunkerConfigSchema();
                
                ServiceRegistrationResponse.Builder registrationBuilder = ServiceRegistrationResponse.newBuilder()
                        .setModuleName("chunker")
                        .setHealthCheckPassed(true);

                if (schemaOptional.isPresent()) {
                    String jsonSchema = schemaOptional.get();
                    registrationBuilder.setJsonConfigSchema(jsonSchema);
                    registrationBuilder.setHealthCheckMessage("Chunker module is healthy and ready to process documents. " +
                                                            "Using dynamically generated ChunkerConfig schema from OpenAPI 3.1.");
                    LOG.debugf("Successfully extracted ChunkerConfig schema (%d characters) using SchemaExtractorService", 
                             jsonSchema.length());
                } else {
                    registrationBuilder.setHealthCheckPassed(false);
                    registrationBuilder.setHealthCheckMessage("Failed to extract ChunkerConfig schema from OpenAPI document");
                    LOG.error("SchemaExtractorService could not extract ChunkerConfig schema");
                }

                LOG.info("Returned service registration for chunker module using SchemaExtractorService");
                return registrationBuilder.build();

            } catch (Exception e) {
                LOG.error("Error getting service registration", e);
                return ServiceRegistrationResponse.newBuilder()
                    .setModuleName("chunker")
                    .setHealthCheckPassed(false)
                    .setHealthCheckMessage("Error getting service registration: " + e.getMessage())
                    .build();
            }
        });
    }


    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        LOG.info("TestProcessData called - executing test version of chunker processing");

        // For test processing, we use the same logic as processData but:
        // 1. Don't write to any output buffers
        // 2. Add a test marker to the logs
        // 3. Use a test document if none provided

        if (request == null || !request.hasDocument()) {
            // Create a test document for validation
            PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("test-doc-" + System.currentTimeMillis())
                .setBody("This is a test document for chunker validation. It contains enough text to be chunked into multiple pieces. " +
                         "The chunker will process this text and create overlapping chunks according to the configuration. " +
                         "This helps verify that the chunker module is functioning correctly.")
                .build();

            ServiceMetadata testMetadata = ServiceMetadata.newBuilder()
                .setStreamId("test-stream")
                .setPipeStepName("test-step")
                .build();

            ProcessConfiguration testConfig = ProcessConfiguration.newBuilder()
                .setCustomJsonConfig(Struct.newBuilder()
                    .putFields("source_field", com.google.protobuf.Value.newBuilder()
                        .setStringValue("body").build())
                    .putFields("chunk_size", com.google.protobuf.Value.newBuilder()
                        .setNumberValue(50).build())
                    .putFields("overlap_size", com.google.protobuf.Value.newBuilder()
                        .setNumberValue(10).build())
                    .build())
                .build();

            request = ModuleProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(testMetadata)
                .setConfig(testConfig)
                .build();
        }

        // Process using regular logic but without side effects
        return processDataInternal(request, true);
    }

    private Uni<ModuleProcessResponse> processDataInternal(ModuleProcessRequest request, boolean isTest) {
        return Uni.createFrom().item(() -> {
            try {
                // Same processing logic as processData
                PipeDoc inputDoc = request.getDocument();
                ProcessConfiguration config = request.getConfig();
                ServiceMetadata metadata = request.getMetadata();
                String streamId = metadata.getStreamId();
                String pipeStepName = metadata.getPipeStepName();

                String logPrefix = isTest ? "[TEST] " : "";
                LOG.infof("%sProcessing document ID: %s for step: %s in stream: %s", 
                    logPrefix, 
                    inputDoc != null && inputDoc.getId() != null ? inputDoc.getId() : "unknown", 
                    pipeStepName, streamId);

                ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder();
                PipeDoc.Builder outputDocBuilder = inputDoc != null ? inputDoc.toBuilder() : PipeDoc.newBuilder();

                // If there's no document in non-test mode, return success but with a log message
                if (!isTest && !request.hasDocument()) {
                    LOG.info("No document provided in request");
                    return ModuleProcessResponse.newBuilder()
                            .setSuccess(true)
                            .addProcessorLogs("Chunker service: no document to process. Chunker service successfully processed request.")
                            .build();
                }

                // Parse chunker config - prefer ChunkerConfig over legacy ChunkerOptions
                ChunkerConfig chunkerConfig;
                Struct customJsonConfig = config.getCustomJsonConfig();
                if (customJsonConfig != null && customJsonConfig.getFieldsCount() > 0) {
                    try {
                        // Try to parse as ChunkerConfig first
                        chunkerConfig = objectMapper.readValue(
                                JsonFormat.printer().print(customJsonConfig),
                                ChunkerConfig.class
                        );
                    } catch (Exception e) {
                        LOG.warnf("Failed to parse as ChunkerConfig, falling back to ChunkerOptions: %s", e.getMessage());
                        // Fallback to ChunkerOptions for backward compatibility
                        ChunkerOptions legacyOptions = objectMapper.readValue(
                                JsonFormat.printer().print(customJsonConfig),
                                ChunkerOptions.class
                        );
                        // Convert ChunkerOptions to ChunkerConfig
                        chunkerConfig = ChunkerConfig.create(
                            ChunkerConfig.DEFAULT_ALGORITHM, // Use default algorithm
                            legacyOptions.sourceField(),
                            legacyOptions.chunkSize(),
                            legacyOptions.chunkOverlap(),
                            legacyOptions.preserveUrls()
                        );
                    }
                } else {
                    chunkerConfig = ChunkerConfig.createDefault();
                }

                if (chunkerConfig.sourceField() == null || chunkerConfig.sourceField().isEmpty()) {
                    return createErrorResponse("Missing 'sourceField' in ChunkerConfig", null);
                }

                // Create chunks using ChunkerConfig for better ID generation
                ChunkingResult chunkingResult = overlapChunker.createChunks(inputDoc, chunkerConfig, streamId, pipeStepName);
                List<Chunk> chunkRecords = chunkingResult.chunks();

                if (!chunkRecords.isEmpty()) {
                    Map<String, String> placeholderToUrlMap = chunkingResult.placeholderToUrlMap();
                    SemanticProcessingResult.Builder newSemanticResultBuilder = SemanticProcessingResult.newBuilder()
                            .setResultId(UUID.randomUUID().toString())
                            .setSourceFieldName(chunkerConfig.sourceField())
                            .setChunkConfigId(chunkerConfig.configId());

                    String resultSetName = String.format(
                            "%s_chunks_%s",
                            pipeStepName,
                            chunkerConfig.configId()
                    ).replaceAll("[^a-zA-Z0-9_\\-]", "_");
                    newSemanticResultBuilder.setResultSetName(resultSetName);

                    int currentChunkNumber = 0;
                    for (Chunk chunkRecord : chunkRecords) {
                        // Sanitize the chunk text to ensure valid UTF-8
                        String sanitizedText = UnicodeSanitizer.sanitizeInvalidUnicode(chunkRecord.text());

                        ChunkEmbedding.Builder chunkEmbeddingBuilder = ChunkEmbedding.newBuilder()
                                .setTextContent(sanitizedText)
                                .setChunkId(chunkRecord.id())
                                .setOriginalCharStartOffset(chunkRecord.originalIndexStart())
                                .setOriginalCharEndOffset(chunkRecord.originalIndexEnd())
                                .setChunkConfigId(chunkerConfig.configId());

                        boolean containsUrlPlaceholder = (chunkerConfig.preserveUrls() != null && chunkerConfig.preserveUrls()) &&
                                !placeholderToUrlMap.isEmpty() &&
                                placeholderToUrlMap.keySet().stream().anyMatch(ph -> chunkRecord.text().contains(ph));

                        Map<String, com.google.protobuf.Value> extractedMetadata = metadataExtractor.extractAllMetadata(
                                sanitizedText,
                                currentChunkNumber,
                                chunkRecords.size(),
                                containsUrlPlaceholder
                        );

                        SemanticChunk.Builder semanticChunkBuilder = SemanticChunk.newBuilder()
                                .setChunkId(chunkRecord.id())
                                .setChunkNumber(currentChunkNumber)
                                .setEmbeddingInfo(chunkEmbeddingBuilder.build())
                                .putAllMetadata(extractedMetadata);

                        newSemanticResultBuilder.addChunks(semanticChunkBuilder.build());
                        currentChunkNumber++;
                    }
                    outputDocBuilder.addSemanticResults(newSemanticResultBuilder.build());

                    String successMessage = isTest ? 
                        String.format("%sSuccessfully created and added metadata to %d chunks for testing using %s algorithm. Chunker service validated successfully.",
                            logPrefix, chunkRecords.size(), chunkerConfig.algorithm().getValue()) :
                        String.format("%sSuccessfully created and added metadata to %d chunks from source field '%s' into result set '%s' using %s algorithm. Chunker service successfully processed document.",
                            logPrefix, chunkRecords.size(), chunkerConfig.sourceField(), resultSetName, chunkerConfig.algorithm().getValue());

                    responseBuilder.addProcessorLogs(successMessage);
                } else {
                    responseBuilder.addProcessorLogs(String.format("%sNo content in '%s' to chunk for document ID: %s",
                            logPrefix, chunkerConfig.sourceField(), inputDoc.getId()));
                }

                responseBuilder.setSuccess(true);
                PipeDoc outputDoc = outputDocBuilder.build();
                responseBuilder.setOutputDoc(outputDoc);

                // IMPORTANT: Don't add to buffer if this is a test
                if (!isTest) {
                    outputBuffer.add(outputDoc);
                }

                return responseBuilder.build();

            } catch (Exception e) {
                String errorMessage = String.format("Error in ChunkerService test: %s", e.getMessage());
                LOG.error(errorMessage, e);
                return createErrorResponse(errorMessage, e);
            }
        });
    }

    private ModuleProcessResponse createErrorResponse(String errorMessage, Exception e) {
        ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder();
        responseBuilder.setSuccess(false);
        responseBuilder.addProcessorLogs(errorMessage);

        Struct.Builder errorDetailsBuilder = Struct.newBuilder();
        errorDetailsBuilder.putFields("error_message", com.google.protobuf.Value.newBuilder().setStringValue(errorMessage).build());
        if (e != null) {
            errorDetailsBuilder.putFields("error_type", com.google.protobuf.Value.newBuilder().setStringValue(e.getClass().getName()).build());
            if (e.getCause() != null) {
                errorDetailsBuilder.putFields("error_cause", com.google.protobuf.Value.newBuilder().setStringValue(e.getCause().getMessage()).build());
            }
        }
        responseBuilder.setErrorDetails(errorDetailsBuilder.build());
        return responseBuilder.build();
    }
}
