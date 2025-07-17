package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for KafkaInputDefinition.
 */
@QuarkusIntegrationTest
public class KafkaInputDefinitionIT extends KafkaInputDefinitionTestBase {

    @Override
    protected ObjectMapper getObjectMapper() {
        return MapperFactory.getObjectMapper();
    }
}
