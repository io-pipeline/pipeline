package io.pipeline.engine;

import io.pipeline.processor.engine.ConnectorRequest;
import io.pipeline.processor.engine.ConnectorResponse;
import io.pipeline.data.model.PipeDoc;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

/**
 * REST endpoint for testing the ConnectorEngine without needing a full connector client.
 * This simulates what a filesystem-crawler or other connector would send.
 */
@Path("/api/connector/test")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConnectorTestResource {
    
    private static final Logger LOG = Logger.getLogger(ConnectorTestResource.class);
    
    @GrpcClient("pipestream-engine")
    io.pipeline.processor.engine.ConnectorEngine connectorEngine;
    
    /**
     * Simple test endpoint - sends a minimal document through the connector pipeline
     */
    @POST
    @Path("/simple")
    public Uni<Response> testSimpleConnector(@QueryParam("connectorType") @DefaultValue("filesystem-crawler") String connectorType,
                                           @QueryParam("text") @DefaultValue("Hello Pipeline World!") String text) {
        LOG.infof("Testing ConnectorEngine with connector_type: %s", connectorType);
        
        // Create a simple test document
        PipeDoc testDoc = PipeDoc.newBuilder()
            .setId("test-doc-" + UUID.randomUUID().toString().substring(0, 8))
            .setTitle("Test Document")
            .setBody(text)
            .setDocumentType("test")
            .setProcessedDate(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond())
                .build())
            .build();
        
        // Create connector request
        ConnectorRequest request = ConnectorRequest.newBuilder()
            .setConnectorType(connectorType)
            .setConnectorId("test-connector-01")
            .setDocument(testDoc)
            .addTags("test")
            .addTags("demo")
            .build();
        
        // Send to ConnectorEngine via gRPC
        return connectorEngine.processConnectorDoc(request)
            .onItem().transform(connectorResponse -> {
                LOG.infof("ConnectorEngine response: accepted=%s, stream_id=%s", 
                         connectorResponse.getAccepted(), connectorResponse.getStreamId());
                
                return Response.ok()
                    .entity(new TestResult(
                        connectorResponse.getAccepted(),
                        connectorResponse.getStreamId(),
                        connectorResponse.getMessage(),
                        testDoc.getId(),
                        connectorType
                    ))
                    .build();
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to test ConnectorEngine");
                return Response.serverError()
                    .entity(new TestResult(
                        false,
                        null,
                        "Error: " + throwable.getMessage(),
                        testDoc.getId(),
                        connectorType
                    ))
                    .build();
            });
    }
    
    /**
     * Test endpoint that accepts JSON document
     */
    @POST
    @Path("/document")
    public Uni<Response> testWithDocument(TestDocumentRequest testRequest) {
        LOG.infof("Testing ConnectorEngine with custom document from connector_type: %s", 
                 testRequest.connectorType);
        
        // Create document from request
        PipeDoc testDoc = PipeDoc.newBuilder()
            .setId(testRequest.docId != null ? testRequest.docId : "test-doc-" + UUID.randomUUID().toString().substring(0, 8))
            .setTitle(testRequest.title != null ? testRequest.title : "Test Document")
            .setBody(testRequest.body != null ? testRequest.body : "No body provided")
            .setDocumentType(testRequest.documentType != null ? testRequest.documentType : "test")
            .setProcessedDate(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond())
                .build())
            .build();
        
        // Create connector request
        ConnectorRequest request = ConnectorRequest.newBuilder()
            .setConnectorType(testRequest.connectorType != null ? testRequest.connectorType : "filesystem-crawler")
            .setConnectorId(testRequest.connectorId != null ? testRequest.connectorId : "test-connector-01")
            .setDocument(testDoc)
            .addTags("test")
            .build();
        
        // Send to ConnectorEngine
        return connectorEngine.processConnectorDoc(request)
            .onItem().transform(connectorResponse -> 
                Response.ok()
                    .entity(new TestResult(
                        connectorResponse.getAccepted(),
                        connectorResponse.getStreamId(),
                        connectorResponse.getMessage(),
                        testDoc.getId(),
                        testRequest.connectorType
                    ))
                    .build()
            )
            .onFailure().recoverWithItem(throwable -> 
                Response.serverError()
                    .entity(new TestResult(
                        false,
                        null,
                        "Error: " + throwable.getMessage(),
                        testDoc.getId(),
                        testRequest.connectorType
                    ))
                    .build()
            );
    }
    
    /**
     * Result of connector test
     */
    public static class TestResult {
        public boolean accepted;
        public String streamId;
        public String message;
        public String documentId;
        public String connectorType;
        
        public TestResult() {}
        
        public TestResult(boolean accepted, String streamId, String message, String documentId, String connectorType) {
            this.accepted = accepted;
            this.streamId = streamId;
            this.message = message;
            this.documentId = documentId;
            this.connectorType = connectorType;
        }
    }
    
    /**
     * Test document request
     */
    public static class TestDocumentRequest {
        public String connectorType;
        public String connectorId;
        public String docId;
        public String title;
        public String body;
        public String documentType;
    }
}