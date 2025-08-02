package io.pipeline.repository.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service that provides namespaced Redis keys with drive support.
 * In production, applications specify drives explicitly.
 * In tests, each test method gets its own isolated drive.
 */
@ApplicationScoped
public class NamespacedRedisKeyService {
    
    @Inject
    RedisKeyConfig baseConfig;
    
    // Thread-local storage for test drive override
    private static final ThreadLocal<String> testDriveOverride = new ThreadLocal<>();
    
    /**
     * Set a test-specific drive override. Used by tests for complete isolation.
     */
    public static void setTestDriveOverride(String drive) {
        testDriveOverride.set(drive);
    }
    
    /**
     * Clear the test drive override.
     */
    public static void clearTestDriveOverride() {
        testDriveOverride.remove();
    }
    
    /**
     * Get the effective drive name, considering test overrides.
     */
    private String getEffectiveDrive(String requestedDrive) {
        String override = testDriveOverride.get();
        return (override != null && !override.isEmpty()) ? override : requestedDrive;
    }
    
    // Drive-aware key methods
    public String filesystemKey(String drive, String key) {
        return baseConfig.filesystemKey(getEffectiveDrive(drive), key);
    }
    
    public String pipedocKey(String drive, String key) {
        return baseConfig.pipedocKey(getEffectiveDrive(drive), key);
    }
    
    public String requestKey(String drive, String key) {
        return baseConfig.requestKey(getEffectiveDrive(drive), key);
    }
    
    public String bulkKey(String drive, String key) {
        return baseConfig.bulkKey(getEffectiveDrive(drive), key);
    }
    
    public String chainKey(String drive, String key) {
        return baseConfig.chainKey(getEffectiveDrive(drive), key);
    }
    
    public String driveMetadataKey(String drive) {
        return baseConfig.driveMetadataKey(getEffectiveDrive(drive));
    }
    
    public String drivePrefix(String drive) {
        return baseConfig.drivePrefix(getEffectiveDrive(drive));
    }
    
    public String allDrivesKey() {
        return baseConfig.allDrivesKey();
    }
    
    // Generic drive key for custom service namespaces
    public String driveKey(String drive, String serviceNamespace, String key) {
        return baseConfig.driveKey(getEffectiveDrive(drive), serviceNamespace, key);
    }
    
    // Expose the underlying config for direct access when needed
    public RedisKeyConfig getBaseConfig() {
        return baseConfig;
    }
}