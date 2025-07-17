package com.rokkon.pipeline.consul.test;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class NoConsulTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.consul-config.enabled", "false",
            "quarkus.consul.enabled", "false"
        );
    }
}
