package io.pipline.module.testharness;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Test processor for integration testing and as a reference implementation.
 * Includes full observability with metrics and tracing.
 */
@GrpcService
@Singleton
@PipelineAutoRegister(
    moduleType = "test-harness",
    useHttpPort = true,  // Using unified HTTP/gRPC server
    metadata = {"category=testing", "purpose=integration-testing"}
)
public class TestProcessorServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(TestProcessorServiceImpl.class);

    @ConfigProperty(name = "test.processor.name", defaultValue = "test-processor")
    String processorName;

    @ConfigProperty(name = "test.processor.delay.ms", defaultValue = "0")
    Long processingDelayMs;

    @ConfigProperty(name = "test.processor.failure.rate", defaultValue = "0.0")
    Double randomFailureRate;

    @Inject
    MeterRegistry registry;

    /**
     * Sets the processing delay in milliseconds.
     * Used for simulating slow processing scenarios.
     * 
     * @param delayMs The delay in milliseconds
     */
    public void setProcessingDelayMs(long delayMs) {
        this.processingDelayMs = delayMs;
        LOG.infof("Processing delay set to %d ms", delayMs);
    }

    /**
     * Sets the random failure rate (0.0 - 1.0).
     * Used for simulating random failures.
     * Note: This can also be set via configuration property test.processor.failure.rate
     * 
     * @param rate The failure rate (0.0 - 1.0)
     */
    public void setRandomFailureRate(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Failure rate must be between 0.0 and 1.0");
        }
        this.randomFailureRate = rate;
        LOG.infof("Random failure rate set to %.2f", rate);
    }

    private Counter processedDocuments;
    private Counter failedDocuments;
    private Timer processingTimer;

    @jakarta.annotation.PostConstruct
    void init() {
        // Validate configuration
        if (randomFailureRate < 0.0 || randomFailureRate > 1.0) {
            throw new IllegalArgumentException("test.processor.failure.rate must be between 0.0 and 1.0, got: " + randomFailureRate);
        }
        
        if (randomFailureRate > 0.0) {
            LOG.infof("Test processor configured with random failure rate: %.2f", randomFailureRate);
        }
        
        this.processedDocuments = Counter.builder("test.processor.documents.processed")
                .description("Number of documents processed")
                .register(registry);

        this.failedDocuments = Counter.builder("test.processor.documents.failed")
                .description("Number of documents that failed processing")
                .register(registry);

        this.processingTimer = Timer.builder("test.processor.processing.time")
                .description("Time taken to process documents")
                .register(registry);
    }

    @Override
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        return processingTimer.record(() -> processDataInternal(request));
    }

    private Uni<ModuleProcessResponse> processDataInternal(ModuleProcessRequest request) {
        LOG.infof("TestProcessor received request for document: %s",
                request.hasDocument() ? request.getDocument().getId() : "no-document");

        // Simulate random failures if configured
        if (randomFailureRate > 0.0) {
            double random = Math.random();
            if (random < randomFailureRate) {
                LOG.infof("Simulating random failure (random=%.2f, threshold=%.2f)", random, randomFailureRate);

                // Return a failure response instead of throwing an exception
                // This ensures the failure is properly counted by the test
                ModuleProcessResponse errorResponse = ModuleProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("TestProcessor: Simulated random failure")
                        .setErrorDetails(Struct.newBuilder()
                                .putFields("error_type", Value.newBuilder().setStringValue("SimulatedFailure").build())
                                .putFields("error_message", Value.newBuilder().setStringValue("Simulated random failure").build())
                                .build())
                        .build();

                return Uni.createFrom().item(errorResponse);
            }
        }

        // Add artificial delay if configured (for testing timeouts, etc.)
        Uni<Void> delay = processingDelayMs > 0
                ? Uni.createFrom().<Void>nullItem().onItem().delayIt().by(java.time.Duration.ofMillis(processingDelayMs))
                : Uni.createFrom().voidItem();

        return delay.onItem().transformToUni(v -> {
            try {
                ModuleProcessResponse.Builder responseBuilder = ModuleProcessResponse.newBuilder()
                        .setSuccess(true)
                        .addProcessorLogs("TestProcessor: Starting document processing");

                if (request.hasDocument()) {
                    // Process the document
                    PipeDoc doc = request.getDocument();

                    // Log metadata if present
                    if (request.hasMetadata()) {
                        LOG.debugf("Processing document from pipeline: %s, step: %s, hop: %d",
                                request.getMetadata().getPipelineName(),
                                request.getMetadata().getPipeStepName(),
                                request.getMetadata().getCurrentHopNumber());
                    }

                    // Create or update custom_data with processing metadata
                    Struct.Builder customDataBuilder = doc.hasCustomData()
                            ? doc.getCustomData().toBuilder()
                            : Struct.newBuilder();

                    customDataBuilder
                            .putFields("processed_by", Value.newBuilder().setStringValue(processorName).build())
                            .putFields("processing_timestamp", Value.newBuilder().setStringValue(String.valueOf(System.currentTimeMillis())).build())
                            .putFields("test_module_version", Value.newBuilder().setStringValue("1.0.0").build());

                    // Add config params if present
                    if (request.hasConfig() && request.getConfig().getConfigParamsCount() > 0) {
                        request.getConfig().getConfigParamsMap().forEach((key, value) ->
                                customDataBuilder.putFields("config_" + key, Value.newBuilder().setStringValue(value).build())
                        );
                    }

                    // Check mode from config
                    String mode = "test";
                    boolean requireSchema = false;
                    boolean simulateError = false;

                    if (request.hasConfig() && request.getConfig().hasCustomJsonConfig()) {
                        Struct config = request.getConfig().getCustomJsonConfig();
                        if (config.containsFields("mode")) {
                            mode = config.getFieldsMap().get("mode").getStringValue();
                        }
                        if (config.containsFields("requireSchema")) {
                            requireSchema = config.getFieldsMap().get("requireSchema").getBoolValue();
                        }
                        if (config.containsFields("simulateError")) {
                            simulateError = config.getFieldsMap().get("simulateError").getBoolValue();
                        }
                    }

                    // Simulate error if requested
                    if (simulateError) {
                        throw new RuntimeException("Simulated error for testing");
                    }

                    // Schema validation mode
                    if (mode.equals("validate") || requireSchema) {
                        // Check if document has required fields for schema validation
                        if (!doc.hasTitle() || doc.getTitle().isEmpty()) {
                            throw new IllegalArgumentException("Schema validation failed: title is required");
                        }
                        if (!doc.hasBody() || doc.getBody().isEmpty()) {
                            throw new IllegalArgumentException("Schema validation failed: body is required");
                        }
                        responseBuilder.addProcessorLogs("TestProcessor: Schema validation passed");
                    }

                    PipeDoc modifiedDoc = doc.toBuilder()
                            .setCustomData(customDataBuilder.build())
                            .build();

                    responseBuilder.setOutputDoc(modifiedDoc);
                    responseBuilder.addProcessorLogs("TestProcessor: Added metadata to document");
                    responseBuilder.addProcessorLogs("TestProcessor: Document processed successfully");

                    processedDocuments.increment();
                } else {
                    responseBuilder.addProcessorLogs("TestProcessor: No document provided");
                }

                ModuleProcessResponse response = responseBuilder.build();
                LOG.infof("TestProcessor returning success: %s", response.getSuccess());

                return Uni.createFrom().item(response);

            } catch (Exception e) {
                LOG.errorf(e, "Error in TestProcessor");
                failedDocuments.increment();

                ModuleProcessResponse errorResponse = ModuleProcessResponse.newBuilder()
                        .setSuccess(false)
                        .addProcessorLogs("TestProcessor: Error - " + e.getMessage())
                        .setErrorDetails(Struct.newBuilder()
                                .putFields("error_type", Value.newBuilder().setStringValue(e.getClass().getSimpleName()).build())
                                .putFields("error_message", Value.newBuilder().setStringValue(e.getMessage()).build())
                                .build())
                        .build();

                return Uni.createFrom().item(errorResponse);
            }
        });
    }

    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        LOG.debugf("TestProcessor registration requested");

        // 1. Build the base registration response. This part is non-optional and static.
        ServiceRegistrationResponse.Builder responseBuilder = buildBaseRegistration();

        // 2. Perform the health check if requested and amend the response.
        if (request.hasTestRequest()) {
            LOG.debugf("Performing health check with test request");
            return performHealthCheck(request.getTestRequest(), responseBuilder);
        } else {
            // No test request provided, assume healthy
            responseBuilder
                    .setHealthCheckPassed(true)
                    .setHealthCheckMessage("No health check performed - module assumed healthy");
            return Uni.createFrom().item(responseBuilder.build());
        }
    }

    /**
     * Builds the static, non-optional part of the registration response,
     * including the critical JSON schema.
     */
    private ServiceRegistrationResponse.Builder buildBaseRegistration() {
        return ServiceRegistrationResponse.newBuilder()
                .setModuleName("testing-harness")  // Should match quarkus.application.name
                .setJsonConfigSchema("""
                    {
                        "type": "object",
                        "properties": {
                            "mode": {
                                "type": "string",
                                "enum": ["test", "validate", "transform"],
                                "default": "test",
                                "description": "Processing mode: test (passthrough), validate (schema check), transform (modify data)"
                            },
                            "addMetadata": {
                                "type": "boolean",
                                "default": true,
                                "description": "Whether to add processing metadata to custom_data"
                            },
                            "requireSchema": {
                                "type": "boolean",
                                "default": false,
                                "description": "Enforce schema validation regardless of mode"
                            },
                            "simulateError": {
                                "type": "boolean",
                                "default": false,
                                "description": "Simulate processing error for testing error handling"
                            }
                        },
                        "required": ["mode"],
                        "additionalProperties": false
                    }
                    """);
    }

    /**
     * Performs a health check by running a test request through the processor
     * and amends the response builder with the results.
     */
    private Uni<ServiceRegistrationResponse> performHealthCheck(ModuleProcessRequest testRequest, ServiceRegistrationResponse.Builder responseBuilder) {
        return processData(testRequest)
                .map(processResponse -> {
                    if (processResponse.getSuccess()) {
                        responseBuilder
                                .setHealthCheckPassed(true)
                                .setHealthCheckMessage("Test module is healthy and functioning correctly");
                    } else {
                        responseBuilder
                                .setHealthCheckPassed(false)
                                .setHealthCheckMessage("Test module health check failed: " +
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
    }

    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        LOG.info("TestProcessData called - executing test version of processing");

        // For test processing, create a test document if none provided
        if (request == null || !request.hasDocument()) {
            PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("test-doc-" + System.currentTimeMillis())
                    .setTitle("Test Document")
                    .setBody("This is a test document for validation")
                    .build();

            ServiceMetadata testMetadata = ServiceMetadata.newBuilder()
                    .setStreamId("test-stream")
                    .setPipeStepName("test-step")
                    .setPipelineName("test-pipeline")
                    .build();

            ProcessConfiguration testConfig = ProcessConfiguration.newBuilder()
                    .build();

            request = ModuleProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(testMetadata)
                    .setConfig(testConfig)
                    .build();
        }

        // Process normally but with test flag in logs
        return processDataInternal(request)
                .onItem().transform(response -> {
                    // Add test marker to logs
                    ModuleProcessResponse.Builder builder = response.toBuilder();
                    for (int i = 0; i < builder.getProcessorLogsCount(); i++) {
                        builder.setProcessorLogs(i, "[TEST] " + builder.getProcessorLogs(i));
                    }
                    builder.addProcessorLogs("[TEST] Test validation completed successfully");
                    return builder.build();
                });
    }
}
