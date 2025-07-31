package io.pipeline.repository.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for Redis key prefixes to support jailing of pipeline data.
 * This ensures pipeline data is isolated from other Redis uses.
 */
@ConfigMapping(prefix = "pipeline.repository.redis")
public interface RedisKeyConfig {
    
    /**
     * Root prefix for all pipeline data in Redis.
     * This jails all pipeline data to prevent conflicts with other Redis usage.
     */
    @WithDefault("pipeline:")
    String rootPrefix();
    
    /**
     * Prefix for filesystem nodes
     */
    @WithDefault("fs:")
    String filesystemPrefix();
    
    /**
     * Prefix for PipeDoc repository data
     */
    @WithDefault("pipedoc:")
    String pipedocPrefix();
    
    /**
     * Prefix for ProcessRequest repository data
     */
    @WithDefault("request:")
    String requestPrefix();
    
    /**
     * Prefix for bulk operations
     */
    @WithDefault("bulk:")
    String bulkPrefix();
    
    /**
     * Prefix for pipeline chain data
     */
    @WithDefault("chain:")
    String chainPrefix();
    
    // Helper methods to construct full keys
    default String filesystemKey(String key) {
        return rootPrefix() + filesystemPrefix() + key;
    }
    
    default String pipedocKey(String key) {
        return rootPrefix() + pipedocPrefix() + key;
    }
    
    default String requestKey(String key) {
        return rootPrefix() + requestPrefix() + key;
    }
    
    default String bulkKey(String key) {
        return rootPrefix() + bulkPrefix() + key;
    }
    
    default String chainKey(String key) {
        return rootPrefix() + chainPrefix() + key;
    }
}