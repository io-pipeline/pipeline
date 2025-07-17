package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for GrpcTransportConfig serialization/deserialization.
 * Tests gRPC transport configuration with Consul service discovery.
 */
public abstract class GrpcTransportConfigTestBase {

    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testBasicConfiguration() {
        GrpcTransportConfig config = new GrpcTransportConfig(
                "document-processor-service",
                Map.of("timeout", "30000", "retry", "3")
        );
        
        assertEquals("document-processor-service", config.serviceName());
        assertEquals("30000", config.grpcClientProperties().get("timeout"));
        assertEquals("3", config.grpcClientProperties().get("retry"));
    }

    @Test
    public void testEmptyProperties() {
        GrpcTransportConfig config = new GrpcTransportConfig(
                "simple-service",
                Map.of()
        );
        
        assertEquals("simple-service", config.serviceName());
        assertTrue(config.grpcClientProperties().isEmpty());
    }

    @Test
    public void testNullProperties() {
        GrpcTransportConfig config = new GrpcTransportConfig(
                "null-props-service",
                null
        );
        
        assertEquals("null-props-service", config.serviceName());
        assertTrue(config.grpcClientProperties().isEmpty());
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        GrpcTransportConfig original = new GrpcTransportConfig(
                "metadata-extractor-service",
                Map.of(
                    "timeout", "60000",
                    "retry", "5",
                    "loadBalancingPolicy", "round_robin",
                    "maxInboundMessageSize", "4194304"
                )
        );
        
        String json = getObjectMapper().writeValueAsString(original);
        
        // Verify JSON structure
        assertTrue(json.contains("\"serviceName\":\"metadata-extractor-service\""));
        assertTrue(json.contains("\"timeout\":\"60000\""));
        assertTrue(json.contains("\"retry\":\"5\""));
        assertTrue(json.contains("\"loadBalancingPolicy\":\"round_robin\""));
        
        // Deserialize
        GrpcTransportConfig deserialized = getObjectMapper().readValue(json, GrpcTransportConfig.class);
        
        assertEquals(original.serviceName(), deserialized.serviceName());
        assertEquals(original.grpcClientProperties(), deserialized.grpcClientProperties());
    }

    @Test
    public void testMinimalSerialization() throws Exception {
        String json = """
            {
                "serviceName": "minimal-service"
            }
            """;
        
        GrpcTransportConfig config = getObjectMapper().readValue(json, GrpcTransportConfig.class);
        
        assertEquals("minimal-service", config.serviceName());
        assertTrue(config.grpcClientProperties().isEmpty());
    }

    @Test
    public void testConsulServiceDiscoveryConfig() {
        // Test typical Consul service name patterns
        GrpcTransportConfig config = new GrpcTransportConfig(
                "rokkon-chunker-v2",
                Map.of(
                    "consul.healthCheck", "true",
                    "consul.tags", "production,v2"
                )
        );
        
        assertEquals("rokkon-chunker-v2", config.serviceName());
        assertEquals("true", config.grpcClientProperties().get("consul.healthCheck"));
        assertEquals("production,v2", config.grpcClientProperties().get("consul.tags"));
    }

    @Test
    public void testImmutability() {
        Map<String, String> mutableProps = new java.util.HashMap<>();
        mutableProps.put("timeout", "5000");
        
        GrpcTransportConfig config = new GrpcTransportConfig(
                "immutable-test-service",
                mutableProps
        );
        
        // Try to modify original map
        mutableProps.put("retry", "10");
        
        // Config should not be affected
        assertEquals(1, config.grpcClientProperties().size());
        assertEquals("5000", config.grpcClientProperties().get("timeout"));
        assertFalse(config.grpcClientProperties().containsKey("retry"));
        
        // Try to modify returned map - should be unmodifiable
        try {
            config.grpcClientProperties().put("newKey", "newValue");
            fail("Map should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testRealWorldConfiguration() throws Exception {
        // Test a production-like configuration
        String json = """
            {
                "serviceName": "rokkon-document-processor",
                "grpcClientProperties": {
                    "timeout": "120000",
                    "retry": "3",
                    "retryBackoff": "exponential",
                    "initialBackoff": "1000",
                    "maxBackoff": "30000",
                    "backoffMultiplier": "2.0",
                    "loadBalancingPolicy": "least_request",
                    "maxInboundMessageSize": "10485760",
                    "keepAliveTime": "30000",
                    "keepAliveTimeout": "10000",
                    "consul.healthCheck": "true",
                    "consul.tags": "production,grpc,v3"
                }
            }
            """;
        
        GrpcTransportConfig config = getObjectMapper().readValue(json, GrpcTransportConfig.class);
        
        assertEquals("rokkon-document-processor", config.serviceName());
        assertEquals(12, config.grpcClientProperties().size());
        
        // Verify important properties
        assertEquals("120000", config.grpcClientProperties().get("timeout"));
        assertEquals("3", config.grpcClientProperties().get("retry"));
        assertEquals("least_request", config.grpcClientProperties().get("loadBalancingPolicy"));
        assertEquals("10485760", config.grpcClientProperties().get("maxInboundMessageSize"));
        assertEquals("true", config.grpcClientProperties().get("consul.healthCheck"));
    }

    @Test
    public void testNullServiceName() {
        // Service name can be null - validation is done by OutputTarget
        GrpcTransportConfig config = new GrpcTransportConfig(null, Map.of());
        assertNull(config.serviceName());
        assertTrue(config.grpcClientProperties().isEmpty());
    }
}