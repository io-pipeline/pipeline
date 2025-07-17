package com.rokkon.pipeline.consul.test.config;

import com.rokkon.pipeline.consul.config.ConsulConfigSource.EngineConfig;
import java.util.Optional;

/**
 * Test implementation of EngineConfig for integration tests.
 */
public class TestEngineConfig implements EngineConfig {
    private final int grpcPort;
    private final int restPort;
    private final Optional<String> instanceId;
    private final boolean debug;
    
    public TestEngineConfig() {
        this(49000, 8080, Optional.empty(), false);
    }
    
    public TestEngineConfig(int grpcPort, int restPort, Optional<String> instanceId, boolean debug) {
        this.grpcPort = grpcPort;
        this.restPort = restPort;
        this.instanceId = instanceId;
        this.debug = debug;
    }
    
    @Override
    public int grpcPort() {
        return grpcPort;
    }
    
    @Override
    public int restPort() {
        return restPort;
    }
    
    @Override
    public Optional<String> instanceId() {
        return instanceId;
    }
    
    @Override
    public boolean debug() {
        return debug;
    }
}