package io.pipeline.module.echo;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that explicitly disables registration.
 * This profile uses the application-no-registration.properties configuration.
 */
public class NoRegistrationTestProfile implements QuarkusTestProfile {
    
    @Override
    public String getConfigProfile() {
        return "no-registration";
    }
    
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        // Explicitly disable registration
        config.put("module.auto-register.enabled", "false");
        config.put("module.auto-register.bean.enabled", "false");
        return config;
    }
}