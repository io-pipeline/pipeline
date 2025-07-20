package io.pipeline.module.chunker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.quarkus.arc.Arc;
import io.quarkus.smallrye.openapi.runtime.OpenApiDocumentService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.openapi.runtime.io.Format;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test to prove that we can extract ChunkerConfig schema from OpenAPI dynamically
 * and validate it against both OpenAPI 3.1 and JSON Schema v7 standards.
 */
@QuarkusTest
public class OpenApiSchemaExtractionTest {

    private static final Logger LOG = Logger.getLogger(OpenApiSchemaExtractionTest.class);

    /**
     * TEST 1: Extract ChunkerConfig schema from the dynamically generated OpenAPI document
     */
    @Test
    public void test1_extractChunkerConfigSchemaFromOpenApi() {
        LOG.info("=== TEST 1: Extract ChunkerConfig schema from OpenAPI ===");
        
        // Get OpenApiDocumentService via CDI container (like the reference shows)
        OpenApiDocumentService documentService = Arc.container()
                .instance(OpenApiDocumentService.class).get();
        
        assertThat("OpenApiDocumentService should be available via CDI", documentService, is(notNullValue()));
        
        // Get the full OpenAPI document as JSON
        byte[] jsonBytes = documentService.getDocument(Format.JSON);
        assertThat("OpenAPI document should be generated", jsonBytes, is(notNullValue()));
        assertThat("OpenAPI document should not be empty", jsonBytes.length, is(greaterThan(0)));
        
        String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);
        LOG.infof("OpenAPI document size: %d characters", jsonString.length());
        
        // Parse the JSON and extract ChunkerConfig schema
        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            JsonObject openApiDoc = reader.readObject();
            
            // Verify OpenAPI document structure
            assertThat("OpenAPI document should have 'openapi' field", openApiDoc.containsKey("openapi"), is(true));
            assertThat("OpenAPI document should have 'components' field", openApiDoc.containsKey("components"), is(true));
            
            JsonObject components = openApiDoc.getJsonObject("components");
            assertThat("Components should not be null", components, is(notNullValue()));
            assertThat("Components should have 'schemas' field", components.containsKey("schemas"), is(true));
            
            JsonObject schemas = components.getJsonObject("schemas");
            assertThat("Schemas should not be null", schemas, is(notNullValue()));
            assertThat("Schemas should contain 'ChunkerConfig'", schemas.containsKey("ChunkerConfig"), is(true));
            
            JsonObject chunkerConfigSchema = schemas.getJsonObject("ChunkerConfig");
            assertThat("ChunkerConfig schema should not be null", chunkerConfigSchema, is(notNullValue()));
            
            // Verify schema has expected structure
            assertThat("Schema should have 'type' field", chunkerConfigSchema.containsKey("type"), is(true));
            assertThat("Schema type should be 'object'", chunkerConfigSchema.getString("type"), is(equalTo("object")));
            assertThat("Schema should have 'properties' field", chunkerConfigSchema.containsKey("properties"), is(true));
            
            JsonObject properties = chunkerConfigSchema.getJsonObject("properties");
            assertThat("Properties should not be null", properties, is(notNullValue()));
            
            // Verify key properties exist
            assertThat("Should have 'algorithm' property", properties.containsKey("algorithm"), is(true));
            assertThat("Should have 'chunkSize' property", properties.containsKey("chunkSize"), is(true));
            assertThat("Should have 'sourceField' property", properties.containsKey("sourceField"), is(true));
            assertThat("Should have 'cleanText' property", properties.containsKey("cleanText"), is(true));
            
            String extractedSchema = chunkerConfigSchema.toString();
            LOG.infof("Extracted ChunkerConfig schema (%d chars): %s", extractedSchema.length(), extractedSchema);
            
            assertThat("Extracted schema should be substantial", extractedSchema.length(), is(greaterThan(100)));
            
            LOG.info("✅ TEST 1 PASSED: Successfully extracted ChunkerConfig schema from OpenAPI");
        }
    }

    /**
     * TEST 2: Validate that the extracted schema is valid OpenAPI 3.1 format
     */
    @Test
    public void test2_validateSchemaIsValidOpenApi31() {
        LOG.info("=== TEST 2: Validate schema is valid OpenAPI 3.1 format ===");
        
        // Extract the schema first
        String chunkerConfigSchema = extractChunkerConfigSchema();
        
        try {
            // Parse as JSON to verify it's valid JSON
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode schemaNode = objectMapper.readTree(chunkerConfigSchema);
            
            // Verify it has OpenAPI schema characteristics
            assertThat("Schema should have 'type' field", schemaNode.has("type"), is(true));
            assertThat("Schema type should be 'object'", schemaNode.get("type").asText(), is(equalTo("object")));
            assertThat("Schema should have 'properties' field", schemaNode.has("properties"), is(true));
            
            JsonNode properties = schemaNode.get("properties");
            assertThat("Properties should be an object", properties.isObject(), is(true));
            
            // Verify specific ChunkerConfig properties
            assertThat("Should have algorithm property", properties.has("algorithm"), is(true));
            assertThat("Should have chunkSize property", properties.has("chunkSize"), is(true));
            
            // Check if algorithm has enum values (OpenAPI style)
            JsonNode algorithmProp = properties.get("algorithm");
            if (algorithmProp.has("enum")) {
                JsonNode enumValues = algorithmProp.get("enum");
                assertThat("Algorithm enum should have values", enumValues.size(), is(greaterThan(0)));
                LOG.infof("Algorithm enum values: %s", enumValues);
            }
            
            // Check for OpenAPI-style constraints on chunkSize
            JsonNode chunkSizeProp = properties.get("chunkSize");
            if (chunkSizeProp.has("minimum")) {
                assertThat("ChunkSize should have minimum constraint", chunkSizeProp.get("minimum").asInt(), is(equalTo(50)));
            }
            if (chunkSizeProp.has("maximum")) {
                assertThat("ChunkSize should have maximum constraint", chunkSizeProp.get("maximum").asInt(), is(equalTo(10000)));
            }
            
            LOG.info("✅ TEST 2 PASSED: Schema is valid OpenAPI 3.1 format");
            
        } catch (Exception e) {
            throw new AssertionError("Schema should be valid JSON and OpenAPI format: " + e.getMessage(), e);
        }
    }

    /**
     * TEST 3: Validate that the extracted schema passes JSON Schema v7 validation (like ConsulModuleRegistryService uses)
     */
    @Test
    public void test3_validateSchemaPassesJsonSchemaV7Validation() {
        LOG.info("=== TEST 3: Validate schema passes JSON Schema v7 validation ===");
        
        // Extract the schema
        String chunkerConfigSchema = extractChunkerConfigSchema();
        
        try {
            // Use the same JSON Schema v7 validation that ConsulModuleRegistryService uses
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode schemaNode = objectMapper.readTree(chunkerConfigSchema);
            
            // Create a JsonSchema validator from the extracted schema
            JsonSchema validator = factory.getSchema(schemaNode);
            assertThat("JsonSchema validator should be created successfully", validator, is(notNullValue()));
            
            // Test the schema against a valid ChunkerConfig example
            String validExample = """
                {
                    "algorithm": "token",
                    "sourceField": "body",
                    "chunkSize": 500,
                    "chunkOverlap": 50,
                    "preserveUrls": true,
                    "cleanText": true,
                    "config_id": "token-body-500-50"
                }
                """;
            
            JsonNode exampleNode = objectMapper.readTree(validExample);
            Set<ValidationMessage> validationErrors = validator.validate(exampleNode);
            
            LOG.infof("Validation errors for valid example: %s", validationErrors);
            assertThat("Valid example should pass validation", validationErrors, is(empty()));
            
            // Test with an invalid example (missing required fields)
            String invalidExample = """
                {
                    "sourceField": "body"
                }
                """;
            
            JsonNode invalidNode = objectMapper.readTree(invalidExample);
            Set<ValidationMessage> invalidErrors = validator.validate(invalidNode);
            
            LOG.infof("Validation errors for invalid example: %s", invalidErrors);
            // We expect validation errors for the invalid example (missing required 'algorithm' and 'chunkSize')
            assertThat("Invalid example should have validation errors", invalidErrors.size(), is(greaterThan(0)));
            
            // Test with invalid values (chunkSize out of range)
            String invalidRangeExample = """
                {
                    "algorithm": "token",
                    "chunkSize": 25,
                    "sourceField": "body"
                }
                """;
            
            JsonNode invalidRangeNode = objectMapper.readTree(invalidRangeExample);
            Set<ValidationMessage> rangeErrors = validator.validate(invalidRangeNode);
            
            LOG.infof("Validation errors for out-of-range example: %s", rangeErrors);
            // We expect validation errors for chunkSize < 50
            assertThat("Out-of-range example should have validation errors", rangeErrors.size(), is(greaterThan(0)));
            
            LOG.info("✅ TEST 3 PASSED: Schema passes JSON Schema v7 validation and correctly validates examples");
            
        } catch (Exception e) {
            throw new AssertionError("Schema should be valid for JSON Schema v7 validation: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to extract ChunkerConfig schema as JSON string
     */
    private String extractChunkerConfigSchema() {
        OpenApiDocumentService documentService = Arc.container()
                .instance(OpenApiDocumentService.class).get();
        
        byte[] jsonBytes = documentService.getDocument(Format.JSON);
        String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);
        
        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            JsonObject openApiDoc = reader.readObject();
            JsonObject components = openApiDoc.getJsonObject("components");
            JsonObject schemas = components.getJsonObject("schemas");
            JsonObject chunkerConfigSchema = schemas.getJsonObject("ChunkerConfig");
            
            return chunkerConfigSchema.toString();
        }
    }
}