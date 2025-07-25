package io.pipeline.module.echo;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.api.annotation.ProcessingBuffered;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;

@GrpcService
@Singleton
@PipelineAutoRegister(
    moduleType = "echo-processor",
    useHttpPort = true,  // Using unified HTTP/gRPC server on port 39100
    metadata = {"category=testing", "complexity=simple"}
)
public class EchoServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(EchoServiceImpl.class);

    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @Override
    @ProcessingBuffered(type = PipeDoc.class, enabled = "${processing.buffer.enabled:false}")
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        LOG.debugf("Echo service received document: %s", 
                 request.hasDocument() ? request.getDocument().getId() : "no document");

        // Build response with success status
        ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("Echo service successfully processed document");

        // If there's a document, add metadata and echo it back
        if (request.hasDocument()) {
            PipeDoc originalDoc = request.getDocument();
            PipeDoc.Builder docBuilder = originalDoc.toBuilder();

            // Add or update custom_data with processing metadata
            Struct.Builder customDataBuilder = originalDoc.hasCustomData() 
                    ? originalDoc.getCustomData().toBuilder() 
                    : Struct.newBuilder();

            // Add echo module metadata
            customDataBuilder.putFields("processed_by_echo", Value.newBuilder().setStringValue(applicationName).build());
            customDataBuilder.putFields("echo_timestamp", Value.newBuilder().setStringValue(Instant.now().toString()).build());
            customDataBuilder.putFields("echo_module_version", Value.newBuilder().setStringValue("1.0.0").build());

            // Add request metadata if available
            if (request.hasMetadata()) {
                ServiceMetadata metadata = request.getMetadata();
                if (metadata.getStreamId() != null && !metadata.getStreamId().isEmpty()) {
                    customDataBuilder.putFields("echo_stream_id", Value.newBuilder().setStringValue(metadata.getStreamId()).build());
                }
                if (metadata.getPipeStepName() != null && !metadata.getPipeStepName().isEmpty()) {
                    customDataBuilder.putFields("echo_step_name", Value.newBuilder().setStringValue(metadata.getPipeStepName()).build());
                }
            }

            // Set the updated custom data
            docBuilder.setCustomData(customDataBuilder.build());

            // Add the echo_processed metadata key that tests expect
            docBuilder.putMetadata("echo_processed", "true");

            // Set the updated document in the response
            responseBuilder.setOutputDoc(docBuilder.build());
            responseBuilder.addProcessorLogs("Echo service added metadata to document");
        }

        ModuleProcessResponse response = responseBuilder.build();
        LOG.debugf("Echo service returning success: %s", response.getSuccess());

        return Uni.createFrom().item(response);
    }

    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        LOG.debug("Echo service registration requested");

        // Build a more comprehensive registration response with metadata
        ServiceRegistrationResponse.Builder responseBuilder = ServiceRegistrationResponse.newBuilder()
                .setModuleName(applicationName)
                .setVersion("1.0.0")
                .setDisplayName("Echo Service")
                .setDescription("A simple echo module that returns documents with added metadata")
                .setOwner("Rokkon Team")
                .addTags("pipeline-module")
                .addTags("echo")
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
                            .setHealthCheckMessage("Echo module is healthy and functioning correctly");
                    } else {
                        responseBuilder
                            .setHealthCheckPassed(false)
                            .setHealthCheckMessage("Echo module health check failed: " + 
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

    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        LOG.debug("TestProcessData called - executing test version of processing");

        // For test processing, create a test document if none provided
        if (request == null || !request.hasDocument()) {
            PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("test-doc-" + System.currentTimeMillis())
                    .setTitle("Test Document")
                    .setBody("This is a test document for echo module validation")
                    .build();

            ServiceMetadata testMetadata = ServiceMetadata.newBuilder()
                    .setStreamId("test-stream")
                    .setPipeStepName("test-step")
                    .setPipelineName("test-pipeline")
                    .build();

            request = ModuleProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(testMetadata)
                    .build();
        }

        // Process normally but with test flag in logs
        return processData(request)
                .onItem().transform(response -> {
                    // Add test marker to logs
                    ModuleProcessResponse.Builder builder = response.toBuilder();
                    for (int i = 0; i < builder.getProcessorLogsCount(); i++) {
                        builder.setProcessorLogs(i, "[TEST] " + builder.getProcessorLogs(i));
                    }
                    builder.addProcessorLogs("[TEST] Echo module test validation completed successfully");
                    return builder.build();
                });
    }
}
