package io.pipeline.module.draft;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Example implementation of a pipeline module service.
 * <p>
 * This class demonstrates how to implement a simple pipeline module that processes documents
 * by adding metadata and returning them. It serves as a template for creating new modules.
 * <p>
 * Key features demonstrated:
 * 1. Auto-registration with the pipeline system
 * 2. Processing documents with metadata
 * 3. Service registration and health checks
 * 4. Test processing mode
 */
@GrpcService // Marks this as a gRPC service that Quarkus will expose
@Singleton // Ensures only one instance is created
@PipelineAutoRegister(
    moduleType = "draft-processor", // Type identifier for this module
    useHttpPort = true,  // Using unified HTTP/gRPC server on port 39100
    metadata = {"category=testing", "complexity=simple"} // Additional metadata for discovery
)
public class ExampleServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(ExampleServiceImpl.class);

    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    /**
     * Process a document request.
     * <p>
     * This method is called when a document is sent to this module for processing.
     * It adds metadata to the document and returns it.
     * 
     * @param request The processing request containing the document and metadata
     * @return A response containing the processed document
     */
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        LOG.debugf("Draft service received document: %s", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");

        // Build response with success status
        ProcessResponse.Builder responseBuilder = ProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Draft service successfully processed document");

        // If there's a document, add metadata and return it
        if (request.hasDocument()) {
            PipeDoc originalDoc = request.getDocument();
            PipeDoc.Builder docBuilder = originalDoc.toBuilder();

            // Add or update custom_data with processing metadata
            Struct.Builder customDataBuilder = originalDoc.hasCustomData() 
                    ? originalDoc.getCustomData().toBuilder() 
                    : Struct.newBuilder();

            // Add draft module metadata
            customDataBuilder.putFields("processed_by_draft", Value.newBuilder().setStringValue(applicationName).build());
            customDataBuilder.putFields("draft_timestamp", Value.newBuilder().setStringValue(Instant.now().toString()).build());
            customDataBuilder.putFields("draft_module_version", Value.newBuilder().setStringValue("1.0.0").build());

            // Add request metadata if available
            if (request.hasMetadata()) {
                ServiceMetadata metadata = request.getMetadata();
                if (metadata.getStreamId() != null && !metadata.getStreamId().isEmpty()) {
                    customDataBuilder.putFields("draft_stream_id", Value.newBuilder().setStringValue(metadata.getStreamId()).build());
                }
                if (metadata.getPipeStepName() != null && !metadata.getPipeStepName().isEmpty()) {
                    customDataBuilder.putFields("draft_step_name", Value.newBuilder().setStringValue(metadata.getPipeStepName()).build());
                }
            }

            // Set the updated custom data
            docBuilder.setCustomData(customDataBuilder.build());

            // Add the draft_processed metadata key that tests expect
            docBuilder.putMetadata("draft_processed", "true");

            // Set the updated document in the response
            responseBuilder.setOutputDoc(docBuilder.build());
            responseBuilder.addProcessorLogs("Draft service added metadata to document");
        }

        ProcessResponse response = responseBuilder.build();
        LOG.debugf("Draft service returning success: %s", response.getSuccess());

        return Uni.createFrom().item(response);
    }

    /**
     * Handle service registration requests.
     * 
     * This method is called when the pipeline system needs to register this module.
     * It provides information about the module's capabilities and health status.
     * 
     * @param request The registration request, which may include a test request
     * @return A response containing registration information
     */
    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        LOG.debug("Draft service registration requested");

        // Build a comprehensive registration response with metadata
        ServiceRegistrationResponse.Builder responseBuilder = ServiceRegistrationResponse.newBuilder()
                .setModuleName(applicationName)
                .setVersion("1.0.0")
                .setDisplayName("Draft Service")
                .setDescription("A simple draft module that processes documents and adds metadata")
                .setOwner("Rokkon Team")
                .addTags("pipeline-module")
                .addTags("draft")
                .addTags("processor")
                .setRegistrationTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .setNanos(Instant.now().getNano())
                        .build());

        // Add server info and SDK version
        responseBuilder
                .setServerInfo(System.getProperty("os.name") + " " + System.getProperty("os.version"))
                .setSdkVersion("1.0.0");

        // Add metadata
        responseBuilder
                .putMetadata("implementation_language", "Java")
                .putMetadata("jvm_version", System.getProperty("java.version"));

        // If test request is provided, perform health check
        if (request.hasTestRequest()) {
            LOG.debug("Performing health check with test request");
            return processData(request.getTestRequest())
                .map(processResponse -> {
                    if (processResponse.getSuccess()) {
                        responseBuilder
                            .setHealthCheckPassed(true)
                            .setHealthCheckMessage("Draft module is healthy and functioning correctly");
                    } else {
                        responseBuilder
                            .setHealthCheckPassed(false)
                            .setHealthCheckMessage("Draft module health check failed: " + 
                                (processResponse.hasErrorDetails() ? 
                                    processResponse.getErrorDetails().toString() : 
                                    "Unknown error"));
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
                .setHealthCheckMessage("Service is healthy");
            return Uni.createFrom().item(responseBuilder.build());
        }
    }

    /**
     * Handle test processing requests.
     * <p>
     * This method is called when the pipeline system wants to test this module.
     * It creates a test document if none is provided and processes it with test markers.
     * 
     * @param request The test processing request
     * @return A response containing the processed document with test markers
     */
    @Override
    public Uni<ProcessResponse> testProcessData(ProcessRequest request) {
        LOG.debug("TestProcessData called - executing test version of processing");

        // For test processing, create a test document if none provided
        if (request == null || !request.hasDocument()) {
            PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("test-doc-" + System.currentTimeMillis())
                    .setTitle("Test Document")
                    .setBody("This is a test document for draft module validation")
                    .build();

            ServiceMetadata testMetadata = ServiceMetadata.newBuilder()
                    .setStreamId("test-stream")
                    .setPipeStepName("test-step")
                    .setPipelineName("test-pipeline")
                    .build();

            request = ProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(testMetadata)
                    .build();
        }

        // Process normally but with test flag in logs
        return processData(request)
                .onItem().transform(response -> {
                    // Add test marker to logs
                    ProcessResponse.Builder builder = response.toBuilder();
                    for (int i = 0; i < builder.getProcessorLogsCount(); i++) {
                        builder.setProcessorLogs(i, "[TEST] " + builder.getProcessorLogs(i));
                    }
                    builder.addProcessorLogs("[TEST] Draft module test validation completed successfully");
                    return builder.build();
                });
    }
}
