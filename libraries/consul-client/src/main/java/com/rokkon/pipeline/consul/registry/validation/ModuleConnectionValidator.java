package com.rokkon.pipeline.consul.registry.validation;

import io.smallrye.mutiny.Uni;

/**
 * Interface for validating module connections.
 * Allows different implementations for production and testing scenarios.
 */
public interface ModuleConnectionValidator {

    /**
     * Validates that a module is reachable at the given host:port.
     * 
     * @param host The host to connect to
     * @param port The port to connect to
     * @param moduleName The name of the module (for logging)
     * @return Uni&lt;Boolean&gt; - true if connection successful, false otherwise
     */
    Uni<Boolean> validateConnection(String host, int port, String moduleName);

    /**
     * Validates a gRPC service endpoint.
     * 
     * @param host The host to connect to
     * @param port The port to connect to
     * @return Uni&lt;Boolean&gt; - true if gRPC endpoint is accessible
     */
    Uni<Boolean> validateGrpcConnection(String host, int port);

    /**
     * Validates an HTTP endpoint.
     * 
     * @param host The host to connect to  
     * @param port The port to connect to
     * @return Uni&lt;Boolean&gt; - true if HTTP endpoint is accessible
     */
    Uni<Boolean> validateHttpConnection(String host, int port);
}
