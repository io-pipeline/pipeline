package com.rokkon.pipeline.consul.test;

import io.quarkus.test.Mock;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.mockito.Mockito;

/**
 * Test producer that provides a mock ConsulClient for unit tests.
 * This prevents real Consul connections during testing.
 */
@Mock
@ApplicationScoped
public class TestConsulClientProducer {
    
    @Produces
    @ApplicationScoped
    public ConsulClient consulClient(Vertx vertx) {
        // Return a mock that does nothing by default
        // Tests can use @InjectMock to override this with their own behavior
        return Mockito.mock(ConsulClient.class);
    }
}