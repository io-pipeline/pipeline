package com.rokkon.pipeline.consul.test.config;

import com.rokkon.pipeline.consul.config.ConsulConfigSource.DefaultClusterConfig;

/**
 * Test implementation of DefaultClusterConfig for integration tests.
 */
public class TestDefaultClusterConfig implements DefaultClusterConfig {
    private final String name;
    private final boolean autoCreate;
    private final String description;
    
    public TestDefaultClusterConfig() {
        this("test-default", false, "Test default cluster");
    }
    
    public TestDefaultClusterConfig(String name, boolean autoCreate, String description) {
        this.name = name;
        this.autoCreate = autoCreate;
        this.description = description;
    }
    
    @Override
    public String name() {
        return name;
    }
    
    @Override
    public boolean autoCreate() {
        return autoCreate;
    }
    
    @Override
    public String description() {
        return description;
    }
}