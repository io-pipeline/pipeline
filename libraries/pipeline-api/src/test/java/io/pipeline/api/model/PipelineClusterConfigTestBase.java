package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for PipelineClusterConfig serialization/deserialization.
 * Tests the top-level cluster configuration that contains all pipeline configurations.
 */
public abstract class PipelineClusterConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testValidClusterConfig() {
        // Create pipeline graph config
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
                Map.of("document-pipeline", createTestPipelineConfig())
        );
        
        // Create module map
        PipelineModuleMap moduleMap = new PipelineModuleMap(
                Map.of("parser-module", createTestModuleConfig())
        );
        
        PipelineClusterConfig config = new PipelineClusterConfig(
                "production-cluster",
                graphConfig,
                moduleMap,
                "document-pipeline",
                Set.of("custom-topic-1", "custom-topic-2"),
                Set.of("external-service-1", "external-service-2")
        );
        
        assertEquals("production-cluster", config.clusterName());
        assertEquals("document-pipeline", config.defaultPipelineName());
        assertEquals(Set.of("custom-topic-1", "custom-topic-2"), new HashSet<>(config.allowedKafkaTopics()));
        assertEquals(Set.of("external-service-1", "external-service-2"), new HashSet<>(config.allowedGrpcServices()));
    }

    @Test
    public void testMinimalClusterConfig() {
        // Only cluster name is required
        PipelineClusterConfig config = new PipelineClusterConfig(
                "minimal-cluster",
                null,
                null,
                null,
                null,
                null
        );
        
        assertEquals("minimal-cluster", config.clusterName());
        assertNull(config.pipelineGraphConfig());
        assertNull(config.pipelineModuleMap());
        assertNull(config.defaultPipelineName());
        assertTrue(config.allowedKafkaTopics().isEmpty());
        assertTrue(config.allowedGrpcServices().isEmpty());
    }

    @Test
    public void testClusterNameValidation() {
        Exception exception1 = assertThrows(IllegalArgumentException.class, 
            () -> new PipelineClusterConfig(null, null, null, null, null, null));
        assertEquals("PipelineClusterConfig clusterName cannot be null or blank.", exception1.getMessage());
            
        Exception exception2 = assertThrows(IllegalArgumentException.class, 
            () -> new PipelineClusterConfig("", null, null, null, null, null));
        assertEquals("PipelineClusterConfig clusterName cannot be null or blank.", exception2.getMessage());
            
        Exception exception3 = assertThrows(IllegalArgumentException.class, 
            () -> new PipelineClusterConfig("   ", null, null, null, null, null));
        assertEquals("PipelineClusterConfig clusterName cannot be null or blank.", exception3.getMessage());
    }

    @Test
    public void testAllowedTopicsValidation() {
        // Set.of() throws NullPointerException for null elements
        // This is expected Java behavior - Set.of() does not accept null values
        
        // Test with HashSet to properly test our validation
        Set<String> topicsWithNull = new java.util.HashSet<>();
        topicsWithNull.add("valid-topic");
        topicsWithNull.add(null);
        topicsWithNull.add("another-topic");
        
        Exception exception1 = assertThrows(IllegalArgumentException.class, () -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                topicsWithNull,
                null
        ));
        assertEquals("allowedKafkaTopics cannot contain null or blank strings.", exception1.getMessage());
            
        // Test blank string validation
        Set<String> topicsWithBlank = new java.util.HashSet<>();
        topicsWithBlank.add("valid-topic");
        topicsWithBlank.add("  ");
        topicsWithBlank.add("another-topic");
        
        Exception exception2 = assertThrows(IllegalArgumentException.class, () -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                topicsWithBlank,
                null
        ));
        assertEquals("allowedKafkaTopics cannot contain null or blank strings.", exception2.getMessage());
    }

    @Test
    public void testAllowedServicesValidation() {
        // Test with HashSet to properly test our validation
        Set<String> servicesWithNull = new java.util.HashSet<>();
        servicesWithNull.add("valid-service");
        servicesWithNull.add(null);
        servicesWithNull.add("another-service");
        
        Exception exception1 = assertThrows(IllegalArgumentException.class, () -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                null,
                servicesWithNull
        ));
        assertEquals("allowedGrpcServices cannot contain null or blank strings.", exception1.getMessage());
            
        // Test blank string validation
        Set<String> servicesWithBlank = new java.util.HashSet<>();
        servicesWithBlank.add("valid-service");
        servicesWithBlank.add("  ");
        servicesWithBlank.add("another-service");
        
        Exception exception2 = assertThrows(IllegalArgumentException.class, () -> new PipelineClusterConfig(
                "test-cluster",
                null,
                null,
                null,
                null,
                servicesWithBlank
        ));
        assertEquals("allowedGrpcServices cannot contain null or blank strings.", exception2.getMessage());
    }

    @Test
    public void testImmutability() {
        Set<String> mutableTopics = new java.util.HashSet<>();
        mutableTopics.add("topic1");
        
        Set<String> mutableServices = new java.util.HashSet<>();
        mutableServices.add("service1");
        
        PipelineClusterConfig config = new PipelineClusterConfig(
                "immutable-cluster",
                null,
                null,
                null,
                mutableTopics,
                mutableServices
        );
        
        // Try to modify original sets
        mutableTopics.add("topic2");
        mutableServices.add("service2");
        
        // Config should not be affected
        assertEquals(1, config.allowedKafkaTopics().size());
        assertEquals(1, config.allowedGrpcServices().size());
        
        // Returned sets should be immutable
        // Test that the returned sets are unmodifiable (would throw UnsupportedOperationException if modified)
        try {
            config.allowedKafkaTopics().add("new-topic");
            fail("Expected UnsupportedOperationException for allowedKafkaTopics");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
        
        try {
            config.allowedGrpcServices().add("new-service");
            fail("Expected UnsupportedOperationException for allowedGrpcServices");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        PipelineGraphConfig graphConfig = new PipelineGraphConfig(
                Map.of("test-pipeline", createTestPipelineConfig())
        );
        
        PipelineModuleMap moduleMap = new PipelineModuleMap(
                Map.of("test-module", createTestModuleConfig())
        );
        
        PipelineClusterConfig original = new PipelineClusterConfig(
                "serialization-test-cluster",
                graphConfig,
                moduleMap,
                "test-pipeline",
                Set.of("topic-a", "topic-b"),
                Set.of("service-x", "service-y")
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertTrue(json.contains("\"clusterName\":\"serialization-test-cluster\""));
        assertTrue(json.contains("\"defaultPipelineName\":\"test-pipeline\""));
        assertTrue(json.contains("\"allowedKafkaTopics\""));
        assertTrue(json.contains("\"allowedGrpcServices\""));
        
        // Deserialize
        PipelineClusterConfig deserialized = getObjectMapper().readValue(json, PipelineClusterConfig.class);
        
        assertEquals(original.clusterName(), deserialized.clusterName());
        assertEquals(original.defaultPipelineName(), deserialized.defaultPipelineName());
        assertEquals(original.allowedKafkaTopics(), deserialized.allowedKafkaTopics());
        assertEquals(original.allowedGrpcServices(), deserialized.allowedGrpcServices());
    }

    @Test
    public void testDeserializationFromJson() throws Exception {
        String json = """
            {
                "clusterName": "production-rokkon-cluster",
                "pipelineGraphConfig": {
                    "pipelines": {
                        "document-processing": {
                            "name": "document-processing",
                            "pipelineSteps": {}
                        }
                    }
                },
                "pipelineModuleMap": {
                    "availableModules": {
                        "parser": {
                            "implementationName": "parser",
                            "implementationId": "parser-module-v1"
                        }
                    }
                },
                "defaultPipelineName": "document-processing",
                "allowedKafkaTopics": [
                    "external.events.input",
                    "external.events.output",
                    "audit.topic"
                ],
                "allowedGrpcServices": [
                    "external-nlp-service",
                    "external-translation-service"
                ]
            }
            """;
        
        PipelineClusterConfig config = getObjectMapper().readValue(json, PipelineClusterConfig.class);
        
        assertEquals("production-rokkon-cluster", config.clusterName());
        assertEquals("document-processing", config.defaultPipelineName());
        
        // Verify Kafka topics
        Set<String> expectedTopics = new HashSet<>(Arrays.asList(
            "external.events.input", 
            "external.events.output", 
            "audit.topic"
        ));
        assertEquals(expectedTopics.size(), config.allowedKafkaTopics().size());
        assertTrue(config.allowedKafkaTopics().containsAll(expectedTopics));
        assertTrue(expectedTopics.containsAll(config.allowedKafkaTopics()));
        
        // Verify gRPC services
        Set<String> expectedServices = new HashSet<>(Arrays.asList(
            "external-nlp-service",
            "external-translation-service"
        ));
        assertEquals(expectedServices.size(), config.allowedGrpcServices().size());
        assertTrue(config.allowedGrpcServices().containsAll(expectedServices));
        assertTrue(expectedServices.containsAll(config.allowedGrpcServices()));
        
        // Verify nested objects
        assertNotNull(config.pipelineGraphConfig());
        assertTrue(config.pipelineGraphConfig().pipelines().containsKey("document-processing"));
        assertNotNull(config.pipelineModuleMap());
        assertTrue(config.pipelineModuleMap().availableModules().containsKey("parser"));
    }

    @Test
    public void testRealWorldClusterConfiguration() throws Exception {
        // Test a comprehensive production-like configuration
        String json = """
            {
                "clusterName": "rokkon-prod-us-east-1",
                "pipelineGraphConfig": {
                    "pipelines": {
                        "document-indexing": {
                            "name": "document-indexing",
                            "pipelineSteps": {
                                "parser": {
                                    "stepName": "tika-parser",
                                    "stepType": "INITIAL_PIPELINE",
                                    "processorInfo": {
                                        "grpcServiceName": "tika-parser-service"
                                    }
                                }
                            }
                        },
                        "realtime-analysis": {
                            "name": "realtime-analysis",
                            "pipelineSteps": {
                                "analyzer": {
                                    "stepName": "nlp-analyzer",
                                    "stepType": "PIPELINE",
                                    "processorInfo": {
                                        "grpcServiceName": "nlp-service"
                                    }
                                }
                            }
                        }
                    }
                },
                "pipelineModuleMap": {
                    "availableModules": {
                        "tika-parser": {
                            "implementationName": "tika-parser",
                            "implementationId": "tika-parser-v2",
                            "customConfigSchemaReference": {
                                "subject": "tika-config-v1",
                                "version": 1
                            }
                        },
                        "nlp-analyzer": {
                            "implementationName": "nlp-analyzer",
                            "implementationId": "nlp-analyzer-v1"
                        }
                    }
                },
                "defaultPipelineName": "document-indexing",
                "allowedKafkaTopics": [
                    "external.documents.input",
                    "external.events.stream",
                    "audit.all-events",
                    "monitoring.metrics",
                    "cross-region.sync"
                ],
                "allowedGrpcServices": [
                    "external-ocr-service",
                    "external-translation-api",
                    "legacy-search-service",
                    "third-party-enrichment"
                ]
            }
            """;
        
        PipelineClusterConfig config = getObjectMapper().readValue(json, PipelineClusterConfig.class);
        
        assertEquals("rokkon-prod-us-east-1", config.clusterName());
        assertEquals(2, config.pipelineGraphConfig().pipelines().size());
        assertEquals(2, config.pipelineModuleMap().availableModules().size());
        assertEquals(5, config.allowedKafkaTopics().size());
        assertEquals(4, config.allowedGrpcServices().size());
        
        // Verify specific pipeline exists
        assertTrue(config.pipelineGraphConfig().pipelines().containsKey("document-indexing"));
        assertTrue(config.pipelineGraphConfig().pipelines().containsKey("realtime-analysis"));
        
        // Verify module configuration
        PipelineModuleConfiguration tikaModule = config.pipelineModuleMap().availableModules().get("tika-parser");
        assertEquals("tika-parser", tikaModule.implementationName());
        assertNotNull(tikaModule.customConfigSchemaReference());
        assertEquals("tika-config-v1", tikaModule.customConfigSchemaReference().subject());
    }

    // Helper methods to create test objects
    private PipelineConfig createTestPipelineConfig() {
        return new PipelineConfig("test-pipeline", Map.of());
    }
    
    private PipelineModuleConfiguration createTestModuleConfig() {
        return new PipelineModuleConfiguration(
            "test-module",
            "test-module-id",
            null,
            Map.of()
        );
    }
}