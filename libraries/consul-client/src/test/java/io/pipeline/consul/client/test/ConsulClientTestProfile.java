package io.pipeline.consul.client.test;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

/**
 * Test profile configuration for consul-client tests.
 * Provides programmatic test configuration and lifecycle management.
 */
public class ConsulClientTestProfile implements QuarkusTestProfile {
    
    private static final ThreadLocal<TestConfiguration> currentConfig = new ThreadLocal<>();
    
    /**
     * Configure test profile for the given test class.
     * Uses naming conventions to determine appropriate configuration.
     */
    public static void configureFor(Class<?> testClass) {
        TestConfiguration config = new TestConfiguration();
        
        String className = testClass.getSimpleName();
        
        // Apply naming convention-based configuration
        if (className.contains("UnitTest")) {
            config.withoutConsul();
        } else if (className.contains("IntegrationTest") || className.contains("IT")) {
            config.withConsul();
        }
        
        if (className.contains("NoScheduler")) {
            config.withoutScheduler();
        }
        
        configure(config);
    }
    
    /**
     * Configure test profile with specific configuration.
     */
    public static void configure(TestConfiguration config) {
        currentConfig.set(config);
        applyConfiguration(config);
    }
    
    /**
     * Reset test profile configuration.
     */
    public static void reset() {
        currentConfig.remove();
    }
    
    /**
     * Get current test configuration.
     */
    public static TestConfiguration getCurrentConfig() {
        return currentConfig.get();
    }
    
    /**
     * Implementation of QuarkusTestProfile interface.
     * Returns configuration overrides for Quarkus tests.
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        TestConfiguration config = getCurrentConfig();
        if (config != null) {
            return config.getProperties();
        }
        
        // Default configuration for when no specific config is set
        TestConfiguration defaultConfig = new TestConfiguration().withoutConsul();
        return defaultConfig.getProperties();
    }
    
    /**
     * Apply the configuration to system properties.
     */
    private static void applyConfiguration(TestConfiguration config) {
        for (Map.Entry<String, String> entry : config.getProperties().entrySet()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Test configuration builder.
     */
    public static class TestConfiguration {
        private final Map<String, String> properties = new HashMap<>();
        
        public TestConfiguration() {
            // Default configuration
            withConfig("quarkus.log.level", "INFO");
        }
        
        public TestConfiguration withConsul() {
            withConfig("quarkus.consul.enabled", "true");
            return this;
        }
        
        public TestConfiguration withoutConsul() {
            withConfig("quarkus.consul.enabled", "false");
            return this;
        }
        
        public TestConfiguration withScheduler() {
            withConfig("quarkus.scheduler.enabled", "true");
            return this;
        }
        
        public TestConfiguration withoutScheduler() {
            withConfig("quarkus.scheduler.enabled", "false");
            return this;
        }
        
        public TestConfiguration withConfig(String key, String value) {
            properties.put(key, value);
            return this;
        }
        
        public Map<String, String> getProperties() {
            return new HashMap<>(properties);
        }
    }
}