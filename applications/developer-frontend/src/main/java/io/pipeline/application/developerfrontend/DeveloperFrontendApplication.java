package io.pipeline.application.developerfrontend;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Developer Frontend Application
 * 
 * This is a minimal Quarkus application that serves as a standalone frontend
 * for testing and developing pipeline modules. It provides a pure Node.js/Vue.js
 * interface for connecting directly to modules via gRPC for development purposes.
 */
@ApplicationPath("/api")
public class DeveloperFrontendApplication extends Application {
    // Minimal application class to satisfy Quarkus requirements
    // The real functionality is in the Vue.js frontend
}