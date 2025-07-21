package io.pipeline.engine;

import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.service.PipelineConfigService;
import io.pipeline.data.model.PipeStream;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.model.StepExecutionRecord;
import io.pipeline.data.module.MutinyPipeStepProcessorGrpc;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.data.module.ProcessConfiguration;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.dynamic.grpc.client.DynamicGrpcClientFactory;
import com.google.protobuf.Struct;
import io.pipeline.stream.engine.PipeStreamEngine;
import io.pipeline.stream.engine.PipeStreamResponse;
import io.pipeline.stream.engine.ProcessStatus;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * PipeStreamEngine - Core orchestration engine for pipeline execution.
 * <p>
 * This service routes data between modules in the pipeline network graph.
 * All three methods essentially do the same routing logic but with different
 * response patterns (test, async, streaming).
 */
@Singleton
@GrpcService
public class PipeStreamEngineImpl implements PipeStreamEngine {

    private static final Logger LOG = Logger.getLogger(PipeStreamEngineImpl.class);
    
    @Inject
    DynamicGrpcClientFactory grpcClientFactory;
    
    @Inject
    PipelineConfigService pipelineConfigService;

    @Override
    public Uni<PipeStream> testPipeStream(PipeStream request) {
        LOG.infof("testPipeStream called - stream_id: %s, target_step: %s", 
                 request.getStreamId(), request.getTargetStepName());
        
        // Use the actual routing logic for testing
        return routeToStep(request)
                .onItem().invoke(processedStream -> 
                    LOG.infof("testPipeStream completed - stream_id: %s, final_hop: %d", 
                             processedStream.getStreamId(), processedStream.getCurrentHopNumber()));
    }

    @Override
    public Uni<PipeStreamResponse> processPipeAsync(PipeStream request) {
        LOG.infof("processPipeAsync called - stream_id: %s, target_step: %s", 
                 request.getStreamId(), request.getTargetStepName());
        
        // Process the stream through the pipeline and convert result to response
        return routeToStep(request)
                .map(processedStream -> {
                    LOG.infof("Pipeline routing completed successfully for stream %s with %d hops", 
                             processedStream.getStreamId(), processedStream.getCurrentHopNumber());
                    
                    PipeStreamResponse response = PipeStreamResponse.newBuilder()
                            .setStreamId(processedStream.getStreamId())
                            .setStatus(ProcessStatus.ACCEPTED)
                            .setMessage("Pipeline processing completed successfully")
                            .setRequestId(UUID.randomUUID().toString())
                            .setTimestamp(Instant.now().toEpochMilli())
                            .build();
                    
                    LOG.infof("processPipeAsync completed - stream_id: %s, final_hop: %d", 
                             processedStream.getStreamId(), processedStream.getCurrentHopNumber());
                    return response;
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.errorf(error, "Pipeline processing failed for stream: %s", request.getStreamId());
                    return PipeStreamResponse.newBuilder()
                            .setStreamId(request.getStreamId())
                            .setStatus(ProcessStatus.ERROR)
                            .setMessage("Pipeline processing failed: " + error.getMessage())
                            .setRequestId(UUID.randomUUID().toString())
                            .setTimestamp(Instant.now().toEpochMilli())
                            .build();
                });
    }

    @Override
    public Multi<PipeStreamResponse> processPipeStream(Multi<PipeStream> request) {
        LOG.info("processPipeStream called - streaming mode");
        
        return request
                .onItem().invoke(pipeStream -> 
                    LOG.infof("Processing stream item - stream_id: %s", pipeStream.getStreamId()))
                .onItem().transformToUniAndMerge(this::processPipeAsync);
    }

    /**
     * Extract cluster and pipeline context from stream metadata.
     * No defaults - all values must be present in the stream.
     */
    Uni<PipelineContext> extractClusterAndPipeline(PipeStream stream) {
        LOG.debugf("Extracting context from stream %s", stream.getStreamId());
        
        String pipelineName = stream.getCurrentPipelineName();
        if (pipelineName == null || pipelineName.isBlank()) {
            LOG.errorf("Pipeline name missing in stream %s", stream.getStreamId());
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Pipeline name not specified in stream"));
        }
        
        // Extract cluster name from context params
        String clusterName = stream.getContextParamsMap().get("cluster");
        if (clusterName == null || clusterName.isBlank()) {
            LOG.errorf("Cluster name missing in context params for stream %s", stream.getStreamId());
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Cluster name not specified in stream context params"));
        }
        
        LOG.debugf("Extracted context - cluster: %s, pipeline: %s for stream %s", 
                  clusterName, pipelineName, stream.getStreamId());
        
        return Uni.createFrom().item(new PipelineContext(clusterName, pipelineName));
    }

    /**
     * Get step configuration using our convenience methods.
     */
    private Uni<PipelineStepConfig> getStepConfig(PipelineContext context, String stepName) {
        return pipelineConfigService.getPipeStep(context.clusterName(), context.pipelineName(), stepName)
                .flatMap(stepOpt -> {
                    if (stepOpt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException("Step '" + stepName + "' not found in pipeline '" 
                                        + context.pipelineName() + "'"));
                    }
                    return Uni.createFrom().item(stepOpt.get());
                });
    }

    /**
     * Call the module service using dynamic gRPC - pure functional!
     */
    private Uni<PipeStream> callModuleService(PipeStream stream, PipelineStepConfig stepConfig) {
        String serviceName = stepConfig.processorInfo().grpcServiceName();
        
        if (serviceName == null || serviceName.isBlank()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("No gRPC service configured for step: " + stepConfig.stepName()));
        }
        
        LOG.infof("Calling service '%s' for step '%s' (stream: %s)", 
                 serviceName, stepConfig.stepName(), stream.getStreamId());
        
        return grpcClientFactory.getMutinyClientForService(serviceName)
                .onItem().invoke(client -> 
                    LOG.infof("Obtained gRPC client for service '%s' (step: %s)", serviceName, stepConfig.stepName()))
                .flatMap(client -> {
                    // Build the ModuleProcessRequest from PipeStream data
                    ModuleProcessRequest request = buildModuleRequest(stream, stepConfig);
                    LOG.infof("Built ModuleProcessRequest for service '%s' with document id: %s", 
                             serviceName, request.getDocument().getId());
                    
                    // Make the gRPC call
                    LOG.infof("Making gRPC call to service '%s' for step '%s'", serviceName, stepConfig.stepName());
                    return client.processData(request)
                            .onItem().invoke(response -> 
                                LOG.infof("Received response from service '%s': success=%s", serviceName, response.getSuccess()))
                            .map(response -> handleModuleResponse(stream, stepConfig, response))
                            .onFailure().transform(error -> {
                                LOG.errorf(error, "gRPC call failed for service '%s', step '%s'", 
                                          serviceName, stepConfig.stepName());
                                return new RuntimeException("Module call failed: " + error.getMessage(), error);
                            });
                });
    }

    /**
     * Build ModuleProcessRequest from PipeStream data
     */
    private ModuleProcessRequest buildModuleRequest(PipeStream stream, PipelineStepConfig stepConfig) {
        // Build ServiceMetadata
        ServiceMetadata.Builder metadataBuilder = ServiceMetadata.newBuilder()
                .setPipelineName(stream.getCurrentPipelineName())
                .setPipeStepName(stepConfig.stepName())
                .setStreamId(stream.getStreamId())
                .setCurrentHopNumber(stream.getCurrentHopNumber())
                .putAllContextParams(stream.getContextParamsMap());
        
        // Add execution history if available
        if (stream.getHistoryList() != null) {
            metadataBuilder.addAllHistory(stream.getHistoryList());
        }
        
        // Build ProcessConfiguration
        ProcessConfiguration.Builder configBuilder = ProcessConfiguration.newBuilder();
        
        // Add custom JSON config if available
        if (stepConfig.customConfig() != null && stepConfig.customConfig().jsonConfig() != null) {
            // Convert JsonNode to Struct
            Struct.Builder structBuilder = Struct.newBuilder();
            // For now, use empty struct - we can enhance this later to convert JsonNode to Struct
            configBuilder.setCustomJsonConfig(structBuilder.build());
        }
        
        // Add config params
        if (stepConfig.customConfig() != null && stepConfig.customConfig().configParams() != null) {
            configBuilder.putAllConfigParams(stepConfig.customConfig().configParams());
        }
        
        // Build the complete request
        return ModuleProcessRequest.newBuilder()
                .setDocument(stream.getDocument()) // PipeDoc from the stream
                .setConfig(configBuilder.build())
                .setMetadata(metadataBuilder.build())
                .build();
    }

    /**
     * Handle ModuleProcessResponse and update PipeStream with results
     * Creates proper audit trail with execution records for the pipeline flow
     */
    private PipeStream handleModuleResponse(PipeStream originalStream, 
                                          PipelineStepConfig stepConfig, 
                                          ModuleProcessResponse response) {
        LOG.infof("Module response received - success: %s, step: %s (stream: %s)", 
                 response.getSuccess(), stepConfig.stepName(), originalStream.getStreamId());
        
        if (!response.getSuccess()) {
            LOG.errorf("Module processing failed for step '%s': %s", 
                      stepConfig.stepName(), response.getErrorDetails());
            throw new RuntimeException("Module processing failed: " + response.getErrorDetails());
        }
        
        long startTime = System.currentTimeMillis();
        long newHopNumber = originalStream.getCurrentHopNumber() + 1;
        
        // Create updated PipeStream with processed document and incremented hop count
        PipeStream.Builder streamBuilder = originalStream.toBuilder()
                .setCurrentHopNumber(newHopNumber);
        
        // Update document if the module returned one
        if (response.hasOutputDoc()) {
            LOG.infof("Module returned updated document for step '%s' (stream: %s)", 
                     stepConfig.stepName(), originalStream.getStreamId());
            streamBuilder.setDocument(response.getOutputDoc());
        }
        
        // Create execution record for this step
        StepExecutionRecord.Builder recordBuilder = StepExecutionRecord.newBuilder()
                .setHopNumber(newHopNumber)
                .setStepName(stepConfig.stepName())
                .setStartTime(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(startTime / 1000)
                    .setNanos((int) ((startTime % 1000) * 1000000))
                    .build())
                .setEndTime(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(System.currentTimeMillis() / 1000)
                    .setNanos((int) ((System.currentTimeMillis() % 1000) * 1000000))
                    .build())
                .setStatus("SUCCESS");
        
        // Add processor logs if available
        if (!response.getProcessorLogsList().isEmpty()) {
            recordBuilder.addAllProcessorLogs(response.getProcessorLogsList());
            LOG.infof("Added %d processor logs to execution record for step %s", 
                     response.getProcessorLogsList().size(), stepConfig.stepName());
        }
        
        // Add the execution record to history
        streamBuilder.addHistory(recordBuilder.build());
        
        // Create the new PipeStream state
        PipeStream newStream = streamBuilder.build();
        
        LOG.infof("Pipeline state transition: hop %d -> %d, step '%s' processed successfully (stream: %s)", 
                 originalStream.getCurrentHopNumber(), newStream.getCurrentHopNumber(), 
                 stepConfig.stepName(), originalStream.getStreamId());
        
        LOG.infof("Execution history now contains %d records for stream %s", 
                 newStream.getHistoryCount(), newStream.getStreamId());
        
        return newStream;
    }

    /**
     * Continue to next steps based on outputs - recursive routing magic!
     */
    private Uni<PipeStream> continueToNextSteps(PipeStream processedStream, 
                                               PipelineStepConfig currentStep, 
                                               PipelineContext context) {
        if (currentStep.outputs() == null || currentStep.outputs().isEmpty()) {
            LOG.infof("Step '%s' has no outputs - pipeline execution complete for stream %s", 
                     currentStep.stepName(), processedStream.getStreamId());
            return Uni.createFrom().item(processedStream);
        }
        
        LOG.infof("Step '%s' has %d outputs, checking for next gRPC step", 
                 currentStep.stepName(), currentStep.outputs().size());
        
        // For now, handle first gRPC output (synchronous pipeline)
        // TODO: Handle multiple outputs (fan-out) and Kafka outputs (async)
        return currentStep.outputs().entrySet().stream()
                .filter(entry -> entry.getValue().transportType() == io.pipeline.api.model.TransportType.GRPC)
                .findFirst()
                .map(entry -> {
                    String nextStepName = entry.getValue().targetStepName();
                    LOG.infof("Continuing pipeline flow from step '%s' to next step '%s' (stream: %s)", 
                             currentStep.stepName(), nextStepName, processedStream.getStreamId());
                    
                    PipeStream nextStream = processedStream.toBuilder()
                            .setTargetStepName(nextStepName)
                            .build();
                    
                    return routeToStep(nextStream); // Beautiful recursion!
                })
                .orElse(Uni.createFrom().item(processedStream));
    }

    /**
     * Core routing method - orchestrates the entire pipeline flow using functional composition.
     * This is the heart of the pipeline engine!
     */
    private Uni<PipeStream> routeToStep(PipeStream stream) {
        LOG.infof("=== Starting routing for stream %s to step: %s (hop: %d) ===", 
                 stream.getStreamId(), stream.getTargetStepName(), stream.getCurrentHopNumber());
        
        return extractClusterAndPipeline(stream)
                .onItem().invoke(context -> 
                    LOG.infof("Extracted context - cluster: %s, pipeline: %s for stream %s", 
                             context.clusterName(), context.pipelineName(), stream.getStreamId()))
                .flatMap(context -> getStepConfig(context, stream.getTargetStepName()))
                .onItem().invoke(stepConfig -> 
                    LOG.infof("Retrieved step config for '%s' - service: %s (stream: %s)", 
                             stepConfig.stepName(), stepConfig.processorInfo().grpcServiceName(), stream.getStreamId()))
                .flatMap(stepConfig -> callModuleService(stream, stepConfig)
                        .flatMap(processedStream -> {
                            LOG.infof("Module processing completed for step '%s', continuing to next steps (stream: %s)", 
                                     stepConfig.stepName(), processedStream.getStreamId());
                            return continueToNextSteps(processedStream, stepConfig, 
                                    new PipelineContext(
                                            stream.getContextParamsMap().get("cluster"),
                                            stream.getCurrentPipelineName()
                                    ));
                        }));
    }


    /**
     * Simple context record for pipeline execution.
     */
    private record PipelineContext(String clusterName, String pipelineName) {}
}