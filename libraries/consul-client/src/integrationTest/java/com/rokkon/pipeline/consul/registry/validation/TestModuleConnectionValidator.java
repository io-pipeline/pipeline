package com.rokkon.pipeline.consul.registry.validation;

import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Test implementation of ModuleConnectionValidator for integration testing.
 * Can be configured to accept all connections or only specific endpoints.
 * This is not a CDI bean - it's created manually in tests.
 */
public class TestModuleConnectionValidator implements ModuleConnectionValidator {
    
    private static final Logger LOG = Logger.getLogger(TestModuleConnectionValidator.class);
    
    private final Set<String> validEndpoints = new HashSet<>();
    private boolean acceptAllConnections = true;
    
    @Override
    public Uni<Boolean> validateConnection(String host, int port, String moduleName) {
        String endpoint = host + ":" + port;
        
        if (acceptAllConnections) {
            LOG.infof("Test validator accepting connection to %s at %s (accept-all mode)", moduleName, endpoint);
            return Uni.createFrom().item(true);
        }
        
        boolean isValid = validEndpoints.contains(endpoint);
        LOG.infof("Test validator checking connection to %s at %s: %s", 
                  moduleName, endpoint, isValid ? "valid" : "invalid");
        return Uni.createFrom().item(isValid);
    }
    
    @Override
    public Uni<Boolean> validateGrpcConnection(String host, int port) {
        return validateConnection(host, port, "gRPC-service");
    }
    
    @Override
    public Uni<Boolean> validateHttpConnection(String host, int port) {
        return validateConnection(host, port, "HTTP-service");
    }
    
    /**
     * Configure whether to accept all connections (default) or only registered endpoints.
     */
    public void setAcceptAllConnections(boolean acceptAll) {
        this.acceptAllConnections = acceptAll;
        LOG.infof("Test validator configured to %s", acceptAll ? "accept all connections" : "validate against registered endpoints");
    }
    
    /**
     * Register a valid endpoint for validation when not in accept-all mode.
     */
    public void registerValidEndpoint(String host, int port) {
        String endpoint = host + ":" + port;
        validEndpoints.add(endpoint);
        LOG.infof("Test validator registered valid endpoint: %s", endpoint);
    }
    
    /**
     * Clear all registered endpoints.
     */
    public void clearValidEndpoints() {
        validEndpoints.clear();
        LOG.info("Test validator cleared all valid endpoints");
    }
}