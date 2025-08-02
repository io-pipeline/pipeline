package io.pipeline.repository.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for Redis key prefixes to support jailing of pipeline data.
 * This ensures pipeline data is isolated from other Redis uses.
 * 
 * Supports test isolation by allowing an optional test namespace to be injected.
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
    
    /**
     * Drives namespace prefix
     */
    @WithDefault("drives:")
    String drivesPrefix();
    
    // Helper methods to construct full keys with drive support
    default String driveKey(String drive, String serviceNamespace, String key) {
        return rootPrefix() + drivesPrefix() + drive + ":" + serviceNamespace + ":" + key;
    }
    
    default String filesystemKey(String drive, String key) {
        return driveKey(drive, filesystemPrefix().replace(":", ""), key);
    }
    
    default String pipedocKey(String drive, String key) {
        return driveKey(drive, pipedocPrefix().replace(":", ""), key);
    }
    
    default String requestKey(String drive, String key) {
        return driveKey(drive, requestPrefix().replace(":", ""), key);
    }
    
    default String bulkKey(String drive, String key) {
        return driveKey(drive, bulkPrefix().replace(":", ""), key);
    }
    
    default String chainKey(String drive, String key) {
        return driveKey(drive, chainPrefix().replace(":", ""), key);
    }
    
    /**
     * Get the drive metadata key
     */
    default String driveMetadataKey(String drive) {
        return rootPrefix() + drivesPrefix() + drive + ":metadata";
    }
    
    /**
     * Get the list of all drives key
     */
    default String allDrivesKey() {
        return rootPrefix() + drivesPrefix() + "all";
    }
    
    /**
     * Get the full drive prefix for clearing operations
     */
    default String drivePrefix(String drive) {
        return rootPrefix() + drivesPrefix() + drive + ":";
    }
}