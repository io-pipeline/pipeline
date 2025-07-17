package com.rokkon.pipeline.consul.config;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;

/**
 * Utility class for debugging configuration properties at application startup.
 * This class logs important configuration properties and environment variables
 * to help diagnose configuration issues in the application.
 */
@ApplicationScoped
public class ConfigurationDebugger {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationDebugger.class);

    /**
     * Default constructor for CDI.
     */
    public ConfigurationDebugger() {
        // Default constructor for CDI
    }

    /**
     * Method triggered on application startup to log configuration properties.
     * 
     * @param ev The startup event that triggers this method
     */
    void onStart(@Observes StartupEvent ev) {
        Config config = ConfigProvider.getConfig();

        // List all properties that start with "quarkus" and show their sources
        List<String> suspiciousProperties = List.of(
            "quarkus.consul-config.format",
            "quarkus.scheduler.agent.port",
            "quarkus.scheduler.agent.host",
            "quarkus.scheduler.format",
            "quarkus.scheduler.watch.period",
            "quarkus.scheduler.watch.enabled",
            "quarkus.health.extensions.enabled",
            "quarkus.generate-code.grpc.codegen.type",
            "quarkus.consul-config.agent.host-port",
            "quarkus.consul-config.enabled"
        );

        log.info("=== CONFIGURATION DEBUG INFO ===");
        for (String property : suspiciousProperties) {
            try {
                ConfigValue configValue = config.getConfigValue(property);
                if (configValue != null && configValue.getValue() != null) {
                    log.info("Property: {} = {} (from: {})",
                        property, configValue.getValue(), configValue.getSourceName());
                }
            } catch (Exception e) {
                // Property doesn't exist, which is fine
            }
        }

        // Also log all environment variables that start with QUARKUS_
        log.info("=== QUARKUS ENVIRONMENT VARIABLES ===");
        System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("QUARKUS_"))
            .forEach(entry -> log.info("Env Var: {} = {}", entry.getKey(), entry.getValue()));

        log.info("=== END CONFIGURATION DEBUG ===");
    }
}
