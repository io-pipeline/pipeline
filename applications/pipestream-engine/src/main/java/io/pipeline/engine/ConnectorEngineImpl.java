package io.pipeline.engine;

import io.pipeline.processor.engine.ConnectorEngine;
import io.pipeline.processor.engine.ConnectorRequest;
import io.pipeline.processor.engine.ConnectorResponse;
import io.pipeline.data.model.ActionType;
import io.pipeline.data.model.PipeStream;
import io.pipeline.stream.engine.PipeStreamEngine;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import jakarta.annotation.PostConstruct;
import org.jboss.logging.Logger;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.List;

/**
 * ConnectorEngine - Lightweight wrapper that receives connector requests
 * and forwards them to the local PipeStreamEngine for processing.
 */
@Singleton
@GrpcService
public class ConnectorEngineImpl implements ConnectorEngine {
    
    private static final Logger LOG = Logger.getLogger(ConnectorEngineImpl.class);
    
    @GrpcClient("pipestream-engine")
    PipeStreamEngine pipeStreamEngine;
    
    @Inject
    ConsulClient consulClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    void init() {
        LOG.info("ConnectorEngineImpl initialized - ready to forward requests to PipeStreamEngine");
    }
    
    @Override
    public Uni<ConnectorResponse> processConnectorDoc(ConnectorRequest request) {
        LOG.infof("ConnectorEngine received document from connector: %s", request.getConnectorType());
        
        // Basic validation
        if (request.getConnectorType() == null || request.getConnectorType().isEmpty()) {
            return Uni.createFrom().item(ConnectorResponse.newBuilder()
                .setAccepted(false)
                .setMessage("Missing required field: connector_type")
                .build());
        }
        
        if (!request.hasDocument()) {
            return Uni.createFrom().item(ConnectorResponse.newBuilder()
                .setAccepted(false)
                .setMessage("Missing required field: document")
                .build());
        }
        
        // Generate stream ID (use suggested if provided, otherwise generate new)
        String streamId = request.hasSuggestedStreamId() && !request.getSuggestedStreamId().isEmpty()
            ? request.getSuggestedStreamId()
            : generateStreamId(request.getConnectorType());
        
        // Create PipeStream from ConnectorRequest
        PipeStream pipeStream = createPipeStream(request, streamId);
        
        // Forward to PipeStreamEngine for actual processing
        return pipeStreamEngine.processPipeAsync(pipeStream)
            .onItem().transform(processResponse -> {
                LOG.infof("PipeStreamEngine response - stream_id: %s, status: %s", 
                         processResponse.getStreamId(), processResponse.getStatus());
                
                return ConnectorResponse.newBuilder()
                    .setStreamId(processResponse.getStreamId())
                    .setAccepted(true) // If we got here, it was accepted
                    .setMessage(String.format("Document from %s connector forwarded to pipeline engine with stream_id: %s", 
                               request.getConnectorType(), processResponse.getStreamId()))
                    .build();
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to forward document from connector %s to PipeStreamEngine",
                    request.getConnectorType());
                
                return ConnectorResponse.newBuilder()
                    .setStreamId(streamId)
                    .setAccepted(false)
                    .setMessage("Failed to forward to pipeline engine: " + throwable.getMessage())
                    .build();
            });
    }
    
    /**
     * Create PipeStream from ConnectorRequest.
     * This converts the connector's document into a pipeline execution request.
     */
    private PipeStream createPipeStream(ConnectorRequest request, String streamId) {
        // Look up connector configuration from Consul
        String cluster = "dev"; // Default
        String targetStepName = "parse-docs"; // Default
        
        try {
            // Look up connector config in Consul KV store
            String kvKey = "connectors/" + request.getConnectorId();
            LOG.debugf("Looking up connector config for: %s", kvKey);
            
            var kvResult = consulClient.getValue(kvKey)
                .await().indefinitely();
                
            if (kvResult != null && kvResult.getValue() != null) {
                String configJson = kvResult.getValue();
                JsonNode config = objectMapper.readTree(configJson);
                cluster = config.get("cluster").asText("dev");
                
                // Get first pipeline step as target
                JsonNode stepsNode = config.get("pipeline_steps");
                if (stepsNode != null && stepsNode.isArray() && !stepsNode.isEmpty()) {
                    targetStepName = stepsNode.get(0).asText();
                }
                
                LOG.infof("Loaded connector config - cluster: %s, first_step: %s", cluster, targetStepName);
            } else {
                LOG.warnf("No connector config found for %s, using defaults", request.getConnectorId());
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to load connector config for %s, using defaults", request.getConnectorId());
        }
        
        // Build context params - include cluster name and connector context
        var contextParamsBuilder = PipeStream.newBuilder()
            .setStreamId(streamId)
            .setDocument(request.getDocument())
            .setCurrentPipelineName("test-pipeline") // Use existing test pipeline
            .setTargetStepName(targetStepName)
            .setCurrentHopNumber(0)
            .setActionType(ActionType.CREATE) // Connectors always create
            .putContextParams("cluster", cluster)
            .putContextParams("connector_type", request.getConnectorType())
            .putContextParams("connector_id", request.getConnectorId());
            
        // Add any additional context params from the request
        if (request.getContextParamsCount() > 0) {
            contextParamsBuilder.putAllContextParams(request.getContextParamsMap());
        }
        
        return contextParamsBuilder.build();
    }
    
    /**
     * Map connector type to pipeline name.
     * TODO: This should be configurable via Consul
     */
    private String mapConnectorTypeToPipeline(String connectorType) {
        return switch (connectorType.toLowerCase()) {
            case "filesystem-crawler" -> "filesystem-pipeline";
            case "gutenberg" -> "gutenberg-pipeline";
            case "wikipedia" -> "wikipedia-pipeline";
            default -> "default-pipeline";
        };
    }
    
    /**
     * Generate a unique stream ID for tracking this document through the pipeline
     */
    private String generateStreamId(String connectorType) {
        return String.format("%s-%s-%s",
            connectorType.toLowerCase().replace("-", "_"),
            System.currentTimeMillis(),
            UUID.randomUUID().toString().substring(0, 8));
    }
}