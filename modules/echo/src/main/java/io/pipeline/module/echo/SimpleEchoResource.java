package io.pipeline.module.echo;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.Map;

/**
 * Simple REST endpoints for basic echo functionality.
 * These endpoints are used by integration tests.
 */
@Path("/echo")
@Produces(MediaType.APPLICATION_JSON)
public class SimpleEchoResource {
    
    @GET
    public String echo(@QueryParam("message") @DefaultValue("Hello") String message) {
        return "Echo: " + message;
    }
    
    @GET
    @Path("/status")
    public Map<String, Object> status() {
        return Map.of(
            "service", "echo",
            "status", "healthy",
            "timestamp", Instant.now().toString()
        );
    }
}