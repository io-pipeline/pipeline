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

import java.util.UUID;

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
        // For now, use simple mapping - later will read from Consul
        String pipelineName = mapConnectorTypeToPipeline(request.getConnectorType());
        String targetStepName = "first-step"; // TODO: Read from pipeline config in Consul
        
        return PipeStream.newBuilder()
            .setStreamId(streamId)
            .setDocument(request.getDocument())
            .setCurrentPipelineName(pipelineName)
            .setTargetStepName(targetStepName)
            .setCurrentHopNumber(0)
            .setActionType(ActionType.CREATE) // Connectors always create
            .build();
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