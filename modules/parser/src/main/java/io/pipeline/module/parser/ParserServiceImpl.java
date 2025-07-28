package io.pipeline.module.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.common.service.SchemaExtractorService;
import io.pipeline.common.util.ProcessingBuffer;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.pipeline.module.parser.config.ParserConfig;
import io.pipeline.module.parser.util.DocumentParser;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@GrpcService // Marks this as a gRPC service that Quarkus will expose
@Singleton // Ensures only one instance is created
@PipelineAutoRegister(
        moduleType = "parser", // Type identifier for this module
        useHttpPort = true,  // Using unified HTTP/gRPC server on port 39101
        metadata = {"category=processor", "complexity=medium"} // Additional metadata for discovery
)
public class ParserServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(ParserServiceImpl.class);

    @Inject
    @Named("parserOutputBuffer")
    ProcessingBuffer<PipeDoc> outputBuffer;
    
    @Inject
    ObjectMapper objectMapper;

    @Inject
    SchemaExtractorService schemaExtractorService;

    @Override
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        LOG.debugf("Parser service received document: %s", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");

        return Uni.createFrom().item(() -> {
            try {
                ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder()
                        .setSuccess(true);

                if (request.hasDocument()) {
                    // Extract configuration from request
                    ParserConfig config = extractConfiguration(request);

                    // Check if document has blob data to parse
                    if (request.getDocument().hasBlob() && request.getDocument().getBlob().getData().size() > 0) {
                        // Get filename from document blob metadata if available
                        String filename = null;
                        if (request.getDocument().getBlob().hasFilename()) {
                            filename = request.getDocument().getBlob().getFilename();
                        }

                        LOG.debugf("Processing document with filename: %s, config ID: %s", 
                                 filename, config.configId());

                        // Parse the document using Tika
                        PipeDoc parsedDoc = DocumentParser.parseDocument(
                            request.getDocument().getBlob().getData(),
                            config,
                            filename
                        );

                        // Create the output document with the original ID preserved
                        PipeDoc outputDoc = parsedDoc.toBuilder()
                                .setId(request.getDocument().getId())
                                .build();

                        // Add the document to the processing buffer for test data generation
                        outputBuffer.add(outputDoc);
                        LOG.debugf("Added document to processing buffer: %s", outputDoc.getId());

                        responseBuilder.setOutputDoc(outputDoc)
                                .addProcessorLogs("Parser service successfully processed document using Tika")
                                .addProcessorLogs(String.format("Extracted title: '%s'", 
                                        outputDoc.getTitle().isEmpty() ? "none" : outputDoc.getTitle()))
                                .addProcessorLogs(String.format("Extracted body length: %d characters", 
                                        outputDoc.getBody().length()))
                                .addProcessorLogs(String.format("Extracted custom data fields: %d", 
                                        outputDoc.hasCustomData() ? outputDoc.getCustomData().getFieldsCount() : 0));

                        LOG.debugf("Successfully parsed document - title: '%s', body length: %d, custom data fields: %d",
                                 outputDoc.getTitle(), outputDoc.getBody().length(), 
                                 outputDoc.hasCustomData() ? outputDoc.getCustomData().getFieldsCount() : 0);

                    } else {
                        // Document has no blob data to parse - just pass it through
                        responseBuilder.setOutputDoc(request.getDocument())
                                .addProcessorLogs("Parser service received document with no blob data - passing through unchanged");
                        LOG.debug("Document has no blob data to parse - passing through");
                    }

                } else {
                    responseBuilder.addProcessorLogs("Parser service received request with no document");
                    LOG.debug("No document in request to parse");
                }

                return responseBuilder.build();

            } catch (Exception e) {
                LOG.error("Error parsing document: " + e.getMessage(), e);

                return ModuleProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("Parser service failed to process document: " + e.getMessage())
                        .addProcessorLogs("Error type: " + e.getClass().getSimpleName())
                        .build();
            } catch (AssertionError e) {
                LOG.error("Assertion error parsing document: " + e.getMessage(), e);

                return ModuleProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("Parser service failed with assertion error: " + e.getMessage())
                        .addProcessorLogs("This may be a Tika internal issue with the document format")
                        .build();
            } catch (Throwable t) {
                LOG.error("Unexpected error parsing document: " + t.getMessage(), t);

                return ModuleProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("Parser service failed with unexpected error: " + t.getMessage())
                        .addProcessorLogs("Error type: " + t.getClass().getSimpleName())
                        .build();
            }
        });
    }

    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        LOG.debug("Parser service registration requested");

        ServiceRegistrationResponse.Builder responseBuilder = ServiceRegistrationResponse.newBuilder()
                .setModuleName("parser");

        // Use SchemaExtractorService to get the dynamically generated ParserConfig schema
        Optional<String> schemaOptional = schemaExtractorService.getFullOpenApiDocument();
        
        if (schemaOptional.isPresent()) {
            String jsonSchema = schemaOptional.get();
            responseBuilder.setJsonConfigSchema(jsonSchema);
            LOG.debugf("Successfully extracted full OpenAPI schema (%d characters) using SchemaExtractorService", 
                     jsonSchema.length());
            LOG.info("Returning raw OpenAPI schema for parser module.");
        } else {
            responseBuilder.setHealthCheckPassed(false);
            responseBuilder.setHealthCheckMessage("Failed to extract ParserConfig schema from OpenAPI document");
            LOG.error("SchemaExtractorService could not extract ParserConfig schema");
            return Uni.createFrom().item(responseBuilder.build());
        }

        // If test request is provided, perform health check
        if (request.hasTestRequest()) {
            LOG.debug("Performing health check with test request");
            return processData(request.getTestRequest())
                .map(processResponse -> {
                    if (processResponse.getSuccess()) {
                        responseBuilder
                            .setHealthCheckPassed(true)
                            .setHealthCheckMessage("Parser module is healthy - successfully processed test document");
                    } else {
                        responseBuilder
                            .setHealthCheckPassed(false)
                            .setHealthCheckMessage("Parser module health check failed: " + 
                                String.join("; ", processResponse.getProcessorLogsList()));
                    }
                    return responseBuilder.build();
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.error("Health check failed with exception", error);
                    return responseBuilder
                        .setHealthCheckPassed(false)
                        .setHealthCheckMessage("Health check failed with exception: " + error.getMessage())
                        .build();
                });
        } else {
            // No test request provided, assume healthy
            responseBuilder
                .setHealthCheckPassed(true)
                .setHealthCheckMessage("No health check performed - module assumed healthy");
            return Uni.createFrom().item(responseBuilder.build());
        }
    }
    
    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        LOG.debug("TestProcessData called - proxying to processData");
        return processData(request);
    }


    /**
     * Extracts configuration parameters from the process request.
     */
    private ParserConfig extractConfiguration(ModuleProcessRequest request) {
        // Try to extract from custom JSON config first
        if (request.hasConfig() && request.getConfig().hasCustomJsonConfig()) {
            try {
                Struct jsonConfig = request.getConfig().getCustomJsonConfig();
                String jsonString = structToJsonString(jsonConfig);
                LOG.debugf("Parsing ParserConfig from JSON: %s", jsonString);
                
                return objectMapper.readValue(jsonString, ParserConfig.class);
            } catch (Exception e) {
                LOG.warnf("Failed to parse ParserConfig from JSON, using fallback: %s", e.getMessage());
            }
        }
        
        // Fallback to config params with defaults
        Map<String, String> configParams = new TreeMap<>();
        if (request.hasConfig()) {
            configParams.putAll(request.getConfig().getConfigParamsMap());
        }
        
        LOG.debugf("Using default ParserConfig with config params: %s", configParams.keySet());
        return ParserConfig.defaultConfig();
    }
    
    /**
     * Converts a Protobuf Struct to JSON string using Google's JsonFormat utility.
     */
    private String structToJsonString(Struct struct) throws Exception {
        return JsonFormat.printer().print(struct);
    }
}
