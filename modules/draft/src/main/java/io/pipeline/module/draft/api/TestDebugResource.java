package io.pipeline.module.draft.api;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.ModuleProcessRequest;
import io.pipeline.data.module.ModuleProcessResponse;
import io.pipeline.data.module.RegistrationRequest;
import io.pipeline.data.module.ServiceMetadata;
import io.pipeline.module.draft.ExampleServiceImpl;
import io.quarkus.grpc.GrpcService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Debug resource for testing the Draft service via REST endpoints.
 * This provides REST access to the gRPC service for integration testing.
 * It demonstrates how to inject and call the gRPC service directly from a REST endpoint.
 */
@Path("/debug")
@Produces(MediaType.APPLICATION_JSON)
public class TestDebugResource {
    
    private static final Logger LOG = Logger.getLogger(TestDebugResource.class);
    
    @Inject
    @GrpcService
    ExampleServiceImpl draftService;
    
    /**
     * Get debug information about the Draft service.
     * This endpoint retrieves service registration information and returns it as JSON.
     * 
     * @return A map containing debug information about the service
     */
    @GET
    @Path("/info")
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("module", "draft");
        info.put("timestamp", Instant.now().toString());
        
        // Get service registration info
        try {
            var request = RegistrationRequest.newBuilder().build();
            var registration = draftService.getServiceRegistration(request)
                    .onItem().transform(reg -> {
                        Map<String, Object> serviceInfo = new HashMap<>();
                        serviceInfo.put("moduleName", reg.getModuleName());
                        serviceInfo.put("version", reg.getVersion());
                        serviceInfo.put("healthCheckPassed", reg.getHealthCheckPassed());
                        serviceInfo.put("healthCheckMessage", reg.getHealthCheckMessage());
                        serviceInfo.put("description", reg.getDescription());
                        return serviceInfo;
                    })
                    .await().indefinitely();
            
            info.put("serviceInfo", registration);
        } catch (Exception e) {
            LOG.error("Failed to get service registration", e);
            info.put("serviceInfo", Map.of("error", e.getMessage()));
        }
        
        return info;
    }
    
    /**
     * Test the Draft service by processing a test document.
     * This endpoint creates a test document, processes it through the Draft service,
     * and returns the results.
     * 
     * @return A map containing the results of the test
     */
    @GET
    @Path("/test")
    public Map<String, Object> testDraftService() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Create test document
            PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setBody("This is a test draft service document")
                    .setTitle("Test Document")
                    .build();
            
            // Create metadata
            ServiceMetadata metadata = ServiceMetadata.newBuilder()
                    .setPipelineName("debug-test-pipeline")
                    .setPipeStepName("debug-draft-test")
                    .setStreamId(UUID.randomUUID().toString())
                    .setCurrentHopNumber(1)
                    .build();
            
            // Create request
            ModuleProcessRequest request = ModuleProcessRequest.newBuilder()
                    .setDocument(testDoc)
                    .setMetadata(metadata)
                    .build();
            
            // Call the service
            ModuleProcessResponse response = draftService.processData(request)
                    .await().indefinitely();
            
            // Build result
            result.put("success", response.getSuccess());
            result.put("message", "Successfully called test draft service");
            
            if (response.hasOutputDoc()) {
                Map<String, Object> docInfo = new HashMap<>();
                PipeDoc outputDoc = response.getOutputDoc();
                docInfo.put("id", outputDoc.getId());
                docInfo.put("hasBody", outputDoc.hasBody());
                docInfo.put("hasCustomData", outputDoc.hasCustomData());
                if (outputDoc.hasCustomData()) {
                    docInfo.put("customDataKeys", outputDoc.getCustomData().getFieldsMap().keySet());
                }
                result.put("outputDocument", docInfo);
            }
            
            if (!response.getProcessorLogsList().isEmpty()) {
                result.put("processorLogs", response.getProcessorLogsList());
            }
            
        } catch (Exception e) {
            LOG.error("Failed to test draft service", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}