package com.rokkon.pipeline.consul.test;

import io.quarkus.test.Mock;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Test-specific producer for ConsulClient that ensures we connect to the test container.
 * This overrides the normal ConsulClientFactory for integration tests.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestConsulClientProducer {
    
    private static final Logger LOG = Logger.getLogger(TestConsulClientProducer.class);
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "consul.host", defaultValue = "localhost")
    String host;
    
    @ConfigProperty(name = "consul.port", defaultValue = "8500")
    int port;
    
    @PostConstruct
    void init() {
        LOG.infof("TestConsulClientProducer initialized with host: %s, port: %d", host, port);
    }
    
    @Produces
    @ApplicationScoped
    @Alternative
    @Priority(1)
    public ConsulClient consulClient() {
        LOG.infof("Creating test ConsulClient for %s:%d", host, port);
        
        ConsulClientOptions options = new ConsulClientOptions()
                .setHost(host)
                .setPort(port)
                .setTimeout(10000); // 10 second timeout for tests
        
        ConsulClient client = ConsulClient.create(vertx, options);
        
        // Test the connection
        client.agentInfo()
            .onItem().invoke(info -> LOG.infof("Test ConsulClient connected successfully: %s", info))
            .onFailure().invoke(t -> LOG.errorf(t, "Test ConsulClient connection failed"))
            .await().indefinitely(); // Block to ensure connection works
        
        return client;
    }
}