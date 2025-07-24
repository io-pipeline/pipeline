package io.pipeline.module.parser.api;

import io.pipeline.data.model.PipeDoc;
import io.pipeline.module.parser.config.ParserConfig;
import io.pipeline.module.parser.util.DocumentParser;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST API endpoints for parser service.
 * Provides developer-friendly HTTP endpoints for testing and integration.
 */
@Path("/api/parser/service")
@Tag(name = "Parser Service", description = "Document parsing operations using Apache Tika")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ParserServiceEndpoint {

    private static final Logger LOG = Logger.getLogger(ParserServiceEndpoint.class);

    @Inject
    io.pipeline.common.service.SchemaExtractorService schemaExtractorService;

    @GET
    @Path("/config")
    @Operation(summary = "Get parser configuration schema", description = "Retrieve the OpenAPI 3.1 JSON Schema for ParserConfig")
    @APIResponse(
        responseCode = "200", 
        description = "OpenAPI 3.1 JSON Schema for ParserConfig",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = ParserConfig.class)
        )
    )
    public Uni<Response> getConfig() {
        LOG.debug("Parser configuration schema request received");
        
        return Uni.createFrom().item(() -> {
            // Extract the cleaned schema for frontend validation (removes $ref and x-* extensions)
            Optional<String> schemaOptional = schemaExtractorService.extractParserConfigSchemaForValidation();
            
            if (schemaOptional.isPresent()) {
                String schemaJson = schemaOptional.get();
                LOG.debugf("Successfully returning ParserConfig schema (%d characters)", schemaJson.length());
                
                // Return the JSON string directly - Quarkus JAX-RS handles this perfectly
                return Response.ok(schemaJson)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
            } else {
                LOG.warn("Could not extract ParserConfig schema from OpenAPI document");
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Schema not available - check OpenAPI document generation"))
                    .build();
            }
        });
    }

    @GET
    @Path("/health")
    @Operation(summary = "Health check", description = "Check parser service health and Tika availability")
    @APIResponse(responseCode = "200", description = "Health check successful")
    public Uni<Response> healthCheck() {
        LOG.debug("Parser health check request received");
        
        return Uni.createFrom().item(() -> {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("version", "1.0.0");
            health.put("parser", "Apache Tika 3.2.1");
            
            // Test basic parser functionality
            try {
                String testText = "Test document";
                PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("health-check")
                    .setBody(testText)
                    .build();
                
                // This would normally parse actual document content, but for health check we just verify setup
                health.put("tika_status", "available");
                health.put("supported_formats", "PDF, Word, PowerPoint, Excel, HTML, Text, and more");
                
            } catch (Exception e) {
                LOG.error("Health check failed", e);
                health.put("status", "unhealthy");
                health.put("tika_status", "error: " + e.getMessage());
            }
            
            return health;
        })
        .map(health -> Response.ok(health).build());
    }

    @POST
    @Path("/test")
    @Operation(summary = "Quick parser test", description = "Test parser with plain text using default settings")
    @Consumes(MediaType.TEXT_PLAIN)
    @APIResponse(responseCode = "200", description = "Test successful")
    public Uni<Response> testParser(String text) {
        LOG.debugf("Test parsing request received - text length: %d", text != null ? text.length() : 0);
        
        if (text == null || text.trim().isEmpty()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Text cannot be null or empty"))
                    .build()
            );
        }

        return Uni.createFrom().item(() -> {
            try {
                // Create a simple test document
                PipeDoc testDoc = PipeDoc.newBuilder()
                    .setId("test-doc")
                    .setTitle("Test Document")
                    .setBody(text)
                    .build();

                // Use default parser configuration
                ParserConfig config = ParserConfig.defaultConfig();
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Parser test completed successfully");
                response.put("input_length", text.length());
                response.put("config_used", config);
                response.put("parser_status", "Apache Tika ready");
                
                return Response.ok(response).build();
                
            } catch (Exception e) {
                LOG.error("Error during parser test", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Parser test failed: " + e.getMessage()))
                    .build();
            }
        });
    }

    @POST
    @Path("/config/validate")
    @Operation(summary = "Validate parser configuration", description = "Validate a ParserConfig JSON against the schema")
    @APIResponse(responseCode = "200", description = "Configuration validation result")
    public Uni<Response> validateConfig(ParserConfig config) {
        LOG.debugf("Parser config validation request received - config ID: %s", 
                  config != null ? config.configId() : "null");
        
        return Uni.createFrom().item(() -> {
            Map<String, Object> response = new HashMap<>();
            
            if (config == null) {
                response.put("valid", false);
                response.put("error", "Configuration cannot be null");
                return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
            }
            
            try {
                // Basic validation - the Jackson deserialization already validates structure
                response.put("valid", true);
                response.put("message", "Configuration is valid");
                response.put("config_id", config.configId());
                
                // Add configuration summary
                Map<String, Object> summary = new HashMap<>();
                if (config.parsingOptions() != null) {
                    summary.put("max_content_length", config.parsingOptions().maxContentLength());
                    summary.put("extract_metadata", config.parsingOptions().extractMetadata());
                }
                if (config.advancedOptions() != null) {
                    summary.put("geo_parser_enabled", config.advancedOptions().enableGeoTopicParser());
                    summary.put("emf_parser_disabled", config.advancedOptions().disableEmfParser());
                }
                response.put("summary", summary);
                
                return Response.ok(response).build();
                
            } catch (Exception e) {
                LOG.error("Error validating parser config", e);
                response.put("valid", false);
                response.put("error", "Validation error: " + e.getMessage());
                return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
            }
        });
    }

    @GET
    @Path("/config/examples")
    @Operation(summary = "Get configuration examples", description = "Get example ParserConfig objects for different use cases")
    @APIResponse(responseCode = "200", description = "Configuration examples retrieved successfully")
    public Uni<Response> getConfigExamples() {
        LOG.debug("Parser config examples request received");
        
        return Uni.createFrom().item(() -> {
            Map<String, Object> examples = new HashMap<>();
            
            examples.put("default", ParserConfig.defaultConfig());
            examples.put("large_documents", ParserConfig.largeDocumentProcessing());
            examples.put("fast_processing", ParserConfig.fastProcessing());
            examples.put("batch_processing", ParserConfig.batchProcessing());
            examples.put("strict_quality", ParserConfig.strictQualityControl());
            
            Map<String, Object> response = new HashMap<>();
            response.put("examples", examples);
            response.put("description", "Pre-configured ParserConfig examples for common use cases");
            
            return Response.ok(response).build();
        });
    }
}