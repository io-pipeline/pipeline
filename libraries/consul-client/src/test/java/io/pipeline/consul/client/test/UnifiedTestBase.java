package io.pipeline.consul.client.test;

import io.pipeline.consul.client.test.ConsulClientTestProfile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for tests using the ConsulClientTestProfile.
 * Provides convenient methods to configure test features.
 * 
 * Tests can either:
 * 1. Use annotations like @RequiresConsul, @RequiresScheduler
 * 2. Override configureProfile() for programmatic configuration
 * 3. Rely on naming conventions (e.g., *UnitTest gets no-consul config)
 */
public abstract class UnifiedTestBase {
    
    @BeforeEach
    protected void setupTestProfile() {
        // Let ConsulClientTestProfile configure based on the test class
        ConsulClientTestProfile.configureFor(this.getClass());
        
        // Allow test to override with custom configuration
        ConsulClientTestProfile.TestConfiguration customConfig = createCustomConfiguration();
        if (customConfig != null) {
            ConsulClientTestProfile.configure(customConfig);
        }
    }
    
    @AfterEach
    protected void cleanupTestProfile() {
        // Clean up configuration after test
        ConsulClientTestProfile.reset();
    }
    
    /**
     * Override this method to provide custom configuration.
     * Return null to use default configuration based on annotations/conventions.
     * 
     * Example:
     * <pre>
     * protected ConsulClientTestProfile.TestConfiguration createCustomConfiguration() {
     *     return new ConsulClientTestProfile.TestConfiguration()
     *         .withConsul()
     *         .withScheduler()
     *         .withConfig("custom.property", "value");
     * }
     * </pre>
     */
    protected ConsulClientTestProfile.TestConfiguration createCustomConfiguration() {
        return null;
    }
}