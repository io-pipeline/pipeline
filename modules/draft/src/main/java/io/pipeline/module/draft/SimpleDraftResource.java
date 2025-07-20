package io.pipeline.module.draft;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.Map;

/**
 * Simple REST endpoints for basic draft module functionality.
 * These endpoints are used by integration tests and provide a minimal
 * example of how to create REST endpoints for a pipeline module.
 */
@Path("/draft")
@Produces(MediaType.APPLICATION_JSON)
public class SimpleDraftResource {
    
    /**
     * Simple echo endpoint that returns the provided message.
     * 
     * @param message The message to echo
     * @return The echoed message
     */
    @GET
    public String echo(@QueryParam("message") @DefaultValue("Hello") String message) {
        return "Draft Echo: " + message;
    }
    
    /**
     * Status endpoint that returns the current status of the draft module.
     * 
     * @return A map containing status information
     */
    @GET
    @Path("/status")
    public Map<String, Object> status() {
        return Map.of(
            "service", "draft",
            "status", "healthy",
            "timestamp", Instant.now().toString()
        );
    }
}