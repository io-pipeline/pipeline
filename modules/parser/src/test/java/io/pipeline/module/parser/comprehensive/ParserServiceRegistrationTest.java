package io.pipeline.module.parser.comprehensive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pipeline.data.model.Blob;
import io.pipeline.data.model.PipeDoc;
import io.pipeline.data.module.*;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive test for parser service registration functionality.
 * Uses the standardized ModuleRegistrationTester for consistent validation.
 */
@QuarkusTest
public class ParserServiceRegistrationTest {

    private static final Logger LOG = Logger.getLogger(ParserServiceRegistrationTest.class);

    @InjectMock
    @GrpcClient
    PipeStepProcessor parserService;

    @Test
    public void testParserServiceRegistrationWithoutHealthCheck() {
        LOG.info("=== Testing Parser Service Registration Without Health Check ===");

        // Get the registration without test request
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        ServiceRegistrationResponse registration = parserService.getServiceRegistration(request)
                .await().indefinitely();

        // Basic registration validation
        assertThat("Registration response should not be null", registration, is(notNullValue()));
        assertThat("Module name should be 'parser' in registration", registration.getModuleName(), is(equalTo("parser")));
        assertThat("Health check should pass without test request", registration.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate no test performed", registration.getHealthCheckMessage(), containsString("No health check performed"));

        // JSON schema validation
        String schema = registration.getJsonConfigSchema();
        assertThat("JSON config schema should not be null", schema, is(notNullValue()));
        assertThat("JSON config schema should not be empty after trimming", schema.trim(), is(not(emptyString())));
        assertThat("JSON config schema should be substantial (>50 chars)", schema.length(), is(greaterThan(50)));

        // Verify schema contains expected configuration keys
        String[] expectedConfigKeys = {
            "extractMetadata", 
            "maxContentLength", 
            "enableTitleExtraction", 
            "enableGeoTopicParser"
        };

        for (String expectedKey : expectedConfigKeys) {
            assertThat(String.format("Schema should contain configuration key '%s'", expectedKey), schema, containsString(expectedKey));
        }

        // Verify JSON schema structure
        assertThat("Schema should contain '$schema' field", schema, containsString("$schema"));
        assertThat("Schema should contain 'properties' field", schema, containsString("properties"));
        assertThat("Schema should contain 'type' field", schema, containsString("type"));

        LOG.info("✅ Parser service registration test without health check passed!");
    }

    @Test
    public void testParserServiceRegistrationWithHealthCheck() {
        LOG.info("=== Testing Parser Service Registration With Health Check ===");

        // Create a test document with blob data
        ByteString testContent = ByteString.copyFromUtf8("This is a test document for health check");
        Blob testBlob = Blob.newBuilder()
                .setData(testContent)
                .setFilename("health-check.txt")
                .build();

        PipeDoc testDoc = PipeDoc.newBuilder()
                .setId("health-check-doc")
                .setBlob(testBlob)
                .build();

        ProcessRequest processRequest = ProcessRequest.newBuilder()
                .setDocument(testDoc)
                .setMetadata(ServiceMetadata.newBuilder()
                        .setPipelineName("health-check")
                        .setPipeStepName("parser-health")
                        .build())
                .setConfig(ProcessConfiguration.newBuilder()
                        .putConfigParams("extractMetadata", "true")
                        .build())
                .build();

        // Get registration with health check
        RegistrationRequest request = RegistrationRequest.newBuilder()
                .setTestRequest(processRequest)
                .build();

        ServiceRegistrationResponse registration = parserService.getServiceRegistration(request)
                .await().indefinitely();

        // Validate registration
        assertThat("Registration response should not be null", registration, is(notNullValue()));
        assertThat("Module name should be 'parser' in registration with health check", registration.getModuleName(), is(equalTo("parser")));
        assertThat("Health check should pass with test request", registration.getHealthCheckPassed(), is(true));
        assertThat("Health check message should indicate successful test processing", registration.getHealthCheckMessage(), containsString("successfully processed test document"));
        assertThat("Registration should include JSON config schema", registration.hasJsonConfigSchema(), is(true));

        LOG.info("✅ Parser service registration test with health check passed!");
    }

    @Test
    public void testParserServiceRegistrationSchemaValidation() {
        LOG.info("=== Testing Parser Service Registration Schema Validation ===");

        // Get the registration
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        var registration = parserService.getServiceRegistration(request)
                .await().indefinitely();

        assertThat("Module name should be 'parser' in schema validation test", registration.getModuleName(), is(equalTo("parser")));

        // Verify JSON schema is present and comprehensive
        String schema = registration.getJsonConfigSchema();
        assertThat("JSON config schema should not be null", schema, is(notNullValue()));
        assertThat("JSON config schema should not be empty after trimming", schema.trim(), is(not(emptyString())));
        assertThat(String.format("Schema should be comprehensive (>500 chars), but was: %d", schema.length()), schema.length(), is(greaterThan(500)));

        // Verify schema structure
        assertThat("Schema should contain '$schema' field", schema, containsString("$schema"));
        assertThat("Schema should contain 'properties' field", schema, containsString("properties"));
        assertThat("Schema should contain 'type' field", schema, containsString("type"));

        // Verify specific parser configuration options are in schema
        assertThat("Schema should contain 'extractMetadata' configuration", schema, containsString("extractMetadata"));
        assertThat("Schema should contain 'maxContentLength' configuration", schema, containsString("maxContentLength"));
        assertThat("Schema should contain 'enableTitleExtraction' configuration", schema, containsString("enableTitleExtraction"));
        assertThat("Schema should contain 'enableGeoTopicParser' configuration", schema, containsString("enableGeoTopicParser"));

        // Validate the schema is valid JSON
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode schemaNode = objectMapper.readTree(schema);
            
            // Verify it's a valid JSON Schema
            assertThat("Schema should have '$schema' field", schemaNode.has("$schema"), is(true));
            assertThat("Schema should reference json-schema.org", schemaNode.get("$schema").asText(), containsString("json-schema.org"));
            assertThat("Schema should have 'properties' field", schemaNode.has("properties"), is(true));
            assertThat("Schema should have 'type' field", schemaNode.has("type"), is(true));
            assertThat("Schema type should be 'object'", schemaNode.get("type").asText(), is(equalTo("object")));

            // Verify specific properties exist
            JsonNode properties = schemaNode.get("properties");
            assertThat("Properties should contain 'parsingOptions' section", properties.has("parsingOptions"), is(true));
            assertThat("Properties should contain 'advancedOptions' section", properties.has("advancedOptions"), is(true));
            assertThat("Properties should contain 'contentTypeHandling' section", properties.has("contentTypeHandling"), is(true));
            assertThat("Properties should contain 'errorHandling' section", properties.has("errorHandling"), is(true));

            LOG.info("Schema is valid JSON and contains expected structure");
        } catch (Exception e) {
            throw new AssertionError("Schema should be valid JSON but failed to parse: " + e.getMessage(), e);
        }

        LOG.info("✅ Parser service registration schema validation passed!");
    }

    @Test
    public void testParserServiceRegistrationPerformance() {
        LOG.info("=== Testing Parser Service Registration Performance ===");

        // Test that registration is fast (should complete quickly)
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        long startTime = System.currentTimeMillis();

        var registration = parserService.getServiceRegistration(request)
                .await().indefinitely();

        long duration = System.currentTimeMillis() - startTime;

        assertThat("Registration response should not be null", registration, is(notNullValue()));
        assertThat(String.format("Service registration should complete quickly (< 1s), took: %d ms", duration), duration, is(lessThan(1000L)));

        LOG.infof("Registration completed in: %d ms", duration);
        LOG.info("✅ Parser service registration performance test passed!");
    }

    @Test 
    public void testParserServiceRegistrationMultipleCalls() {
        LOG.info("=== Testing Parser Service Registration Multiple Calls ===");

        // Test that multiple calls return consistent results
        RegistrationRequest request = RegistrationRequest.newBuilder().build();
        
        var registration1 = parserService.getServiceRegistration(request)
                .await().indefinitely();
        var registration2 = parserService.getServiceRegistration(request)
                .await().indefinitely();
        var registration3 = parserService.getServiceRegistration(request)
                .await().indefinitely();

        // All registrations should be identical
        assertThat("First and second registration should have same module name", registration1.getModuleName(), is(equalTo(registration2.getModuleName())));
        assertThat("Second and third registration should have same module name", registration2.getModuleName(), is(equalTo(registration3.getModuleName())));

        assertThat("First and second registration should have identical schemas", registration1.getJsonConfigSchema(), is(equalTo(registration2.getJsonConfigSchema())));
        assertThat("Second and third registration should have identical schemas", registration2.getJsonConfigSchema(), is(equalTo(registration3.getJsonConfigSchema())));

        LOG.info("All three registration calls returned identical results");
        LOG.info("✅ Parser service registration consistency test passed!");
    }
}
