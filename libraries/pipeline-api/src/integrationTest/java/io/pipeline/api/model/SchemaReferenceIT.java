package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for SchemaReference.
 * Verifies SchemaReference works without Quarkus injection using the shared ObjectMapper.
 */
@QuarkusIntegrationTest
public class SchemaReferenceIT extends SchemaReferenceTestBase {

    @Override
    protected ObjectMapper getObjectMapper() {
        return MapperFactory.getObjectMapper();
    }
}
