package io.pipeline.consul.client;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Test application for integration tests.
 * This provides a minimal Quarkus application to test the REST endpoints
 * and other features that require a running application.
 */
@QuarkusMain
public class TestApplication {
    
    public static void main(String... args) {
        Quarkus.run(TestApp.class, args);
    }
    
    public static class TestApp implements QuarkusApplication {
        @Override
        public int run(String... args) throws Exception {
            Quarkus.waitForExit();
            return 0;
        }
    }
}