package io.pipeline.data.util;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;


@QuarkusTest
class ResourceLoadingTest {
    
    private static final Logger LOG = Logger.getLogger(ResourceLoadingTest.class);

    @Test
    void testResourceLoading() {
        // Test if we can load a known resource
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        
        // Try to load the first tika request file
        String resourcePath = "test-data/tika/requests/tika_request_000.bin";
        
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            assertNotNull(is);
            LOG.infof("Successfully loaded resource: %s", resourcePath);
        } catch (Exception e) {
            LOG.errorf("Failed to load resource: %s", e.getMessage());
            throw new AssertionError("Could not load resource: " + resourcePath, e);
        }
    }

    @Test
    void testApplicationPropertiesLoading() {
        // Test if basic resources work
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        String resourcePath = "application.properties";
        LOG.infof("Attempting to load: %s", resourcePath);
        
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            assertNotNull(is);
            LOG.info("Successfully loaded application.properties");
        } catch (Exception e) {
            throw new AssertionError("Could not load application.properties", e);
        }
    }
}