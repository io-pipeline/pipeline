package io.pipeline.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration tests for GrpcTransportConfig.
 * <p>
 * This test uses the shared ObjectMapper from MapperFactory.
 */
@QuarkusIntegrationTest
public class GrpcTransportConfigIT extends GrpcTransportConfigTestBase {

    @Override
    protected ObjectMapper getObjectMapper() {
        return MapperFactory.getObjectMapper();
    }
}
