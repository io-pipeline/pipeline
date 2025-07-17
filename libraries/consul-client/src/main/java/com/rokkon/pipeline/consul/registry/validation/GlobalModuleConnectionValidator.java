package com.rokkon.pipeline.consul.registry.validation;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Default implementation of ModuleConnectionValidator that performs actual network connections.
 * This is the standard implementation used in production.
 */
@ApplicationScoped
@LookupIfProperty(name = "pipeline.module.connection.validator", stringValue = "global", lookupIfMissing = true)
public class GlobalModuleConnectionValidator implements ModuleConnectionValidator {

    private static final Logger LOG = Logger.getLogger(GlobalModuleConnectionValidator.class);

    @Inject
    Vertx vertx;

    /**
     * Default constructor for CDI.
     */
    public GlobalModuleConnectionValidator() {
        // Default constructor for CDI
    }

    @Override
    public Uni<Boolean> validateConnection(String host, int port, String moduleName) {
        return vertx.createNetClient()
            .connect(port, host)
            .onItem().transform(socket -> {
                socket.close();
                LOG.infof("Successfully connected to module %s at %s:%d", moduleName, host, port);
                return true;
            })
            .onFailure().recoverWithItem(t -> {
                LOG.warnf("Connection validation failed for module %s at %s:%d - %s",
                    moduleName, host, port, t.getMessage());
                return false;
            });
    }

    @Override
    public Uni<Boolean> validateGrpcConnection(String host, int port) {
        // For now, gRPC validation uses the same TCP connection check
        // In the future, this could perform a gRPC health check
        return validateConnection(host, port, "gRPC-service");
    }

    @Override
    public Uni<Boolean> validateHttpConnection(String host, int port) {
        // For now, HTTP validation uses the same TCP connection check
        // In the future, this could perform an HTTP health check
        return validateConnection(host, port, "HTTP-service");
    }
}
