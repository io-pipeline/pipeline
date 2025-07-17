package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for PipelineModuleMap serialization/deserialization.
 * Tests the catalog of available pipeline modules.
 */
public abstract class PipelineModuleMapTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testValidModuleMap() {
        PipelineModuleConfiguration parserModule = new PipelineModuleConfiguration(
            "TikaParser",
            "com.rokkon.parser.tika",
            new SchemaReference("tika-config", 1),
            Map.of("maxSize", 10485760)
        );
        
        PipelineModuleConfiguration chunkerModule = new PipelineModuleConfiguration(
            "SlidingWindowChunker",
            "com.rokkon.chunker.sliding",
            null,
            Map.of("chunkSize", 1000, "overlap", 100)
        );
        
        PipelineModuleConfiguration classifierModule = new PipelineModuleConfiguration(
            "BertClassifier",
            "com.rokkon.classifier.bert",
            new SchemaReference("bert-config", 2)
        );
        
        Map<String, PipelineModuleConfiguration> modules = Map.of(
            "com.rokkon.parser.tika", parserModule,
            "com.rokkon.chunker.sliding", chunkerModule,
            "com.rokkon.classifier.bert", classifierModule
        );
        
        PipelineModuleMap moduleMap = new PipelineModuleMap(modules);
        
        assertEquals(3, moduleMap.availableModules().size());
        assertTrue(moduleMap.availableModules().containsKey("com.rokkon.parser.tika"));
        assertTrue(moduleMap.availableModules().containsKey("com.rokkon.chunker.sliding"));
        assertTrue(moduleMap.availableModules().containsKey("com.rokkon.classifier.bert"));
        assertEquals("TikaParser", 
            moduleMap.availableModules().get("com.rokkon.parser.tika").implementationName());
    }

    @Test
    public void testEmptyModuleMap() {
        // Empty map is valid
        PipelineModuleMap moduleMap = new PipelineModuleMap(Map.of());
        assertTrue(moduleMap.availableModules().isEmpty());
    }

    @Test
    public void testNullModuleMap() {
        // Null map should become empty map
        PipelineModuleMap moduleMap = new PipelineModuleMap(null);
        assertTrue(moduleMap.availableModules().isEmpty());
    }

    @Test
    public void testNullKeyInMapValidation() {
        Map<String, PipelineModuleConfiguration> mutableMap = new java.util.HashMap<>();
        mutableMap.put("valid.module", new PipelineModuleConfiguration("Valid", "valid.module", null));
        mutableMap.put(null, new PipelineModuleConfiguration("Invalid", "invalid", null));
        
        // Map.copyOf() will throw NPE for null keys
        // This is expected Java behavior - Map.copyOf() does not accept null keys or values
        assertThrows(NullPointerException.class, () -> new PipelineModuleMap(mutableMap));
    }

    @Test
    public void testNullValueInMapValidation() {
        Map<String, PipelineModuleConfiguration> mutableMap = new java.util.HashMap<>();
        mutableMap.put("valid.module", new PipelineModuleConfiguration("Valid", "valid.module", null));
        mutableMap.put("null.module", null);
        
        // Map.copyOf() will throw NPE for null values
        // This is expected Java behavior - Map.copyOf() does not accept null keys or values
        assertThrows(NullPointerException.class, () -> new PipelineModuleMap(mutableMap));
    }

    @Test
    public void testImmutability() {
        Map<String, PipelineModuleConfiguration> mutableMap = new java.util.HashMap<>();
        PipelineModuleConfiguration module = new PipelineModuleConfiguration("Test", "test.module", null);
        mutableMap.put("test.module", module);
        
        PipelineModuleMap moduleMap = new PipelineModuleMap(mutableMap);
        
        // Try to modify original map
        mutableMap.put("another.module", new PipelineModuleConfiguration("Another", "another.module", null));
        
        // ModuleMap should not be affected
        assertEquals(1, moduleMap.availableModules().size());
        assertThrows(UnsupportedOperationException.class, () -> {
            moduleMap.availableModules().put("test", null);
        });
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        PipelineModuleConfiguration enricherModule = new PipelineModuleConfiguration(
            "GeoEnricher",
            "com.rokkon.enricher.geo",
            new SchemaReference("geo-enricher-v1", 1),
            Map.of(
                "provider", "openstreetmap",
                "cacheSize", 10000,
                "timeout", 5000
            )
        );
        
        PipelineModuleConfiguration validatorModule = new PipelineModuleConfiguration(
            "SchemaValidator",
            "com.rokkon.validator.schema",
            null,
            Map.of("strictMode", true)
        );
        
        PipelineModuleMap original = new PipelineModuleMap(Map.of(
            "com.rokkon.enricher.geo", enricherModule,
            "com.rokkon.validator.schema", validatorModule
        ));
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertTrue(json.contains("\"availableModules\""));
        assertTrue(json.contains("\"com.rokkon.enricher.geo\""));
        assertTrue(json.contains("\"GeoEnricher\""));
        assertTrue(json.contains("\"provider\":\"openstreetmap\""));
        
        // Deserialize
        PipelineModuleMap deserialized = getObjectMapper().readValue(json, PipelineModuleMap.class);
        
        assertEquals(2, deserialized.availableModules().size());
        assertEquals("GeoEnricher", 
            deserialized.availableModules().get("com.rokkon.enricher.geo").implementationName());
        assertTrue(deserialized.availableModules().get("com.rokkon.validator.schema").customConfig().containsKey("strictMode"));
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "availableModules": {
                    "com.rokkon.parser.pdf": {
                        "implementationName": "PDFBoxParser",
                        "implementationId": "com.rokkon.parser.pdf",
                        "customConfigSchemaReference": {
                            "subject": "pdfbox-parser-config",
                            "version": 3
                        },
                        "customConfig": {
                            "extractImages": true,
                            "extractTables": true,
                            "maxPages": 1000
                        }
                    },
                    "com.rokkon.chunker.semantic": {
                        "implementationName": "SemanticChunker",
                        "implementationId": "com.rokkon.chunker.semantic",
                        "customConfig": {
                            "model": "sentence-transformers/all-mpnet-base-v2",
                            "similarity_threshold": 0.75
                        }
                    },
                    "com.rokkon.storage.s3": {
                        "implementationName": "S3Storage",
                        "implementationId": "com.rokkon.storage.s3"
                    }
                }
            }
            """;
        
        PipelineModuleMap moduleMap = getObjectMapper().readValue(json, PipelineModuleMap.class);
        
        assertEquals(3, moduleMap.availableModules().size());
        
        // Verify PDF parser module
        PipelineModuleConfiguration pdfParser = moduleMap.availableModules().get("com.rokkon.parser.pdf");
        assertNotNull(pdfParser);
        assertEquals("PDFBoxParser", pdfParser.implementationName());
        assertEquals(true, pdfParser.customConfig().get("extractImages"));
        assertEquals(3, pdfParser.customConfigSchemaReference().version());
        
        // Verify semantic chunker module
        PipelineModuleConfiguration chunker = moduleMap.availableModules().get("com.rokkon.chunker.semantic");
        assertNotNull(chunker);
        assertNull(chunker.customConfigSchemaReference());
        assertEquals(0.75, chunker.customConfig().get("similarity_threshold"));
        
        // Verify S3 storage module (minimal config)
        PipelineModuleConfiguration storage = moduleMap.availableModules().get("com.rokkon.storage.s3");
        assertNotNull(storage);
        assertTrue(storage.customConfig().isEmpty());
        assertNull(storage.customConfigSchemaReference());
    }

    @Test
    public void testEmptyModulesJson() throws Exception {
        String json = """
            {
                "availableModules": {}
            }
            """;
        
        PipelineModuleMap moduleMap = getObjectMapper().readValue(json, PipelineModuleMap.class);
        assertTrue(moduleMap.availableModules().isEmpty());
    }

    @Test
    public void testRealWorldModuleCatalog() throws Exception {
        // Test a comprehensive module catalog like what would be used in production
        String json = """
            {
                "availableModules": {
                    "com.rokkon.parser.tika.full": {
                        "implementationName": "TikaFullParser",
                        "implementationId": "com.rokkon.parser.tika.full",
                        "customConfigSchemaReference": {
                            "subject": "tika-full-parser-v2",
                            "version": 2
                        },
                        "customConfig": {
                            "parseMode": "comprehensive",
                            "enableOCR": true,
                            "languages": ["en", "es", "fr", "de"],
                            "maxFileSize": 104857600
                        }
                    },
                    "com.rokkon.parser.tika.fast": {
                        "implementationName": "TikaFastParser",
                        "implementationId": "com.rokkon.parser.tika.fast",
                        "customConfigSchemaReference": {
                            "subject": "tika-fast-parser-v1",
                            "version": 1
                        },
                        "customConfig": {
                            "parseMode": "fast",
                            "enableOCR": false,
                            "maxFileSize": 10485760
                        }
                    },
                    "com.rokkon.chunker.fixed": {
                        "implementationName": "FixedSizeChunker",
                        "implementationId": "com.rokkon.chunker.fixed",
                        "customConfig": {
                            "chunkSize": 4000,
                            "unit": "tokens"
                        }
                    },
                    "com.rokkon.chunker.sliding": {
                        "implementationName": "SlidingWindowChunker",
                        "implementationId": "com.rokkon.chunker.sliding",
                        "customConfig": {
                            "windowSize": 4000,
                            "stepSize": 3500,
                            "unit": "tokens"
                        }
                    },
                    "com.rokkon.enricher.metadata": {
                        "implementationName": "MetadataEnricher",
                        "implementationId": "com.rokkon.enricher.metadata",
                        "customConfig": {
                            "extractors": ["author", "title", "date", "keywords"],
                            "inferMissing": true
                        }
                    },
                    "com.rokkon.classifier.doctype": {
                        "implementationName": "DocumentTypeClassifier",
                        "implementationId": "com.rokkon.classifier.doctype",
                        "customConfigSchemaReference": {
                            "subject": "doctype-classifier-v3",
                            "version": 3
                        },
                        "customConfig": {
                            "modelPath": "/models/doctype-bert-v3.bin",
                            "categories": ["invoice", "contract", "report", "email", "memo", "other"],
                            "confidenceThreshold": 0.8
                        }
                    }
                }
            }
            """;
        
        PipelineModuleMap moduleMap = getObjectMapper().readValue(json, PipelineModuleMap.class);
        
        assertEquals(6, moduleMap.availableModules().size());
        
        // Verify we have different parser implementations
        long parserCount = moduleMap.availableModules().keySet().stream()
            .filter(key -> key.contains("parser"))
            .count();
        assertEquals(2, parserCount);
        
        // Verify we have different chunker implementations
        long chunkerCount = moduleMap.availableModules().keySet().stream()
            .filter(key -> key.contains("chunker"))
            .count();
        assertEquals(2, chunkerCount);
        
        // Verify document classifier configuration
        PipelineModuleConfiguration classifier = moduleMap.availableModules().get("com.rokkon.classifier.doctype");
        assertTrue(classifier.customConfig().containsKey("categories"));
        assertTrue(classifier.customConfig().containsKey("confidenceThreshold"));
    }
}