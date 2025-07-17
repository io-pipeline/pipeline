package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for PipelineModuleConfiguration serialization/deserialization.
 * Tests pipeline module configuration including custom config handling.
 */
public abstract class PipelineModuleConfigurationTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testValidConfiguration() {
        SchemaReference schemaRef = new SchemaReference("parser-config-schema", 1);
        Map<String, Object> customConfig = Map.of(
            "parserType", "tika",
            "maxFileSize", 10485760,
            "supportedFormats", Map.of(
                "pdf", true,
                "docx", true,
                "html", false
            )
        );
        
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "TikaParser",
            "com.rokkon.parser.tika",
            schemaRef,
            customConfig
        );
        
        assertEquals("TikaParser", config.implementationName());
        assertEquals("com.rokkon.parser.tika", config.implementationId());
        assertEquals(schemaRef, config.customConfigSchemaReference());
        assertEquals("tika", config.customConfig().get("parserType"));
        assertEquals(10485760, config.customConfig().get("maxFileSize"));
    }

    @Test
    public void testMinimalConfiguration() {
        // Custom config and schema reference are optional
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "SimpleModule",
            "com.rokkon.simple",
            null,
            null
        );
        
        assertEquals("SimpleModule", config.implementationName());
        assertEquals("com.rokkon.simple", config.implementationId());
        assertNull(config.customConfigSchemaReference());
        assertTrue(config.customConfig().isEmpty());
    }

    @Test
    public void testConvenienceConstructor() {
        SchemaReference schemaRef = new SchemaReference("enricher-schema", 2);
        
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "EntityEnricher",
            "com.rokkon.enricher.entity",
            schemaRef
        );
        
        assertEquals("EntityEnricher", config.implementationName());
        assertEquals("com.rokkon.enricher.entity", config.implementationId());
        assertEquals(schemaRef, config.customConfigSchemaReference());
        assertTrue(config.customConfig().isEmpty());
    }

    @Test
    public void testNullImplementationNameValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new PipelineModuleConfiguration(
            null,
            "com.rokkon.test",
            null,
            null
        ));
assertEquals("PipelineModuleConfiguration implementationName cannot be null or blank.", exception.getMessage());
    }

    @Test
    public void testBlankImplementationNameValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new PipelineModuleConfiguration(
            "   ",
            "com.rokkon.test",
            null,
            null
        ));
assertEquals("PipelineModuleConfiguration implementationName cannot be null or blank.", exception.getMessage());
    }

    @Test
    public void testNullImplementationIdValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new PipelineModuleConfiguration(
            "TestModule",
            null,
            null,
            null
        ));
assertEquals("PipelineModuleConfiguration implementationId cannot be null or blank.", exception.getMessage());
    }

    @Test
    public void testBlankImplementationIdValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new PipelineModuleConfiguration(
            "TestModule",
            "",
            null,
            null
        ));
assertEquals("PipelineModuleConfiguration implementationId cannot be null or blank.", exception.getMessage());
    }

    @Test
    public void testCustomConfigImmutability() {
        Map<String, Object> mutableConfig = new java.util.HashMap<>();
        mutableConfig.put("key1", "value1");
        
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "ImmutableTest",
            "com.rokkon.immutable",
            null,
            mutableConfig
        );
        
        // Try to modify original map
        mutableConfig.put("key2", "value2");
        
        // Config should not be affected
        assertEquals(1, config.customConfig().size());
        // Check that the map is unmodifiable (would throw UnsupportedOperationException if modified)
        try {
            config.customConfig().put("test", "value");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        PipelineModuleConfiguration original = new PipelineModuleConfiguration(
            "ChunkerModule",
            "com.rokkon.chunker.sliding",
            new SchemaReference("chunker-config-v1", 3),
            Map.of(
                "chunkSize", 1000,
                "overlapSize", 100,
                "strategy", "sliding_window",
                "metadata", Map.of(
                    "preserveHeaders", true,
                    "includePositions", false
                )
            )
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertTrue(json.contains("\"implementationName\":\"ChunkerModule\""));
        assertTrue(json.contains("\"implementationId\":\"com.rokkon.chunker.sliding\""));
        assertTrue(json.contains("\"subject\":\"chunker-config-v1\""));
        assertTrue(json.contains("\"chunkSize\":1000"));
        
        // Deserialize
        PipelineModuleConfiguration deserialized = getObjectMapper().readValue(json, PipelineModuleConfiguration.class);
        
        assertEquals(original.implementationName(), deserialized.implementationName());
        assertEquals(original.implementationId(), deserialized.implementationId());
        assertEquals(original.customConfigSchemaReference(), deserialized.customConfigSchemaReference());
        assertEquals(original.customConfig(), deserialized.customConfig());
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "implementationName": "AdvancedClassifier",
                "implementationId": "com.rokkon.classifier.ml",
                "customConfigSchemaReference": {
                    "subject": "classifier-ml-config",
                    "version": 5
                },
                "customConfig": {
                    "modelType": "bert",
                    "confidence_threshold": 0.85,
                    "categories": ["technical", "business", "legal"],
                    "features": {
                        "useContext": true,
                        "maxTokens": 512
                    }
                }
            }
            """;
        
        PipelineModuleConfiguration config = getObjectMapper().readValue(json, PipelineModuleConfiguration.class);
        
        assertEquals("AdvancedClassifier", config.implementationName());
        assertEquals("com.rokkon.classifier.ml", config.implementationId());
        assertEquals("classifier-ml-config", config.customConfigSchemaReference().subject());
        assertEquals(5, config.customConfigSchemaReference().version());
        assertEquals("bert", config.customConfig().get("modelType"));
        assertEquals(0.85, config.customConfig().get("confidence_threshold"));
        assertTrue(config.customConfig().containsKey("categories"));
    }

    @Test
    public void testNullFieldsOmittedInJson() throws Exception {
        PipelineModuleConfiguration config = new PipelineModuleConfiguration(
            "MinimalModule",
            "com.rokkon.minimal",
            null,
            null
        );
        
        String json = getObjectMapper().writeValueAsString(config);
        
        // Verify null fields are omitted due to @JsonInclude(JsonInclude.Include.NON_NULL)
        assertTrue(json.contains("\"implementationName\":\"MinimalModule\""));
        assertTrue(json.contains("\"implementationId\":\"com.rokkon.minimal\""));
        assertFalse(json.contains("customConfigSchemaReference"));
        assertFalse(json.contains("\"customConfig\":null"));
    }

    @Test
    public void testRealWorldParserConfiguration() throws Exception {
        String json = """
            {
                "implementationName": "ApacheTikaParser",
                "implementationId": "com.rokkon.parser.tika.full",
                "customConfigSchemaReference": {
                    "subject": "tika-parser-config",
                    "version": 2
                },
                "customConfig": {
                    "parseMode": "structured",
                    "maxFileSize": 52428800,
                    "timeout": 30000,
                    "detectLanguage": true,
                    "extractMetadata": true,
                    "ocrConfig": {
                        "enabled": true,
                        "languages": ["eng", "spa", "fra"],
                        "dpi": 300
                    },
                    "contentHandlers": {
                        "pdf": "pdfbox",
                        "office": "poi",
                        "html": "jsoup"
                    }
                }
            }
            """;
        
        PipelineModuleConfiguration config = getObjectMapper().readValue(json, PipelineModuleConfiguration.class);
        
        assertEquals("ApacheTikaParser", config.implementationName());
        assertTrue(config.customConfig().containsKey("ocrConfig"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> ocrConfig = (Map<String, Object>) config.customConfig().get("ocrConfig");
        assertTrue((Boolean) ocrConfig.get("enabled"));
    }
}