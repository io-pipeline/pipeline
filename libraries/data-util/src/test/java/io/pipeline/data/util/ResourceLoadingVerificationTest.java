package io.pipeline.data.util;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ResourceLoadingVerificationTest {

    private static final Logger LOG = Logger.getLogger(ResourceLoadingVerificationTest.class);

    @Test
    void testCanLoadResourceFromClasspath() {
        String resourceName = "verification/resource-loading-test.txt";
        byte[] bytes = null;

        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(is, "InputStream for resource '" + resourceName + "' should not be null");
            bytes = is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load resource: " + resourceName, e);
        }

        assertNotNull(bytes, "Resource content should not be empty");
        assertFalse(bytes.length == 0, "Resource content should not be empty");
        String content = new String(bytes, StandardCharsets.UTF_8);
        assertEquals("Quarkus resource loading can be tricky.", content, "Resource content should match expected value");

        LOG.info("Successfully loaded resource from filesystem: " + resourceName);
    }
}