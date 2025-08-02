package io.pipeline.repository.service;

import io.pipeline.repository.config.NamespacedRedisKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Base class for tests that need isolated Redis drives.
 * Each test method gets its own virtual drive to prevent interference.
 * Drive names are constructed from test class and method names.
 */
public abstract class IsolatedRedisTest {
    
    private String currentTestDrive;
    
    @BeforeEach
    void setupTestIsolation(TestInfo testInfo) {
        // Create a unique drive name for this test method
        String testClassName = testInfo.getTestClass()
            .map(clazz -> clazz.getSimpleName())
            .orElse("UnknownClass");
            
        String testMethodName = testInfo.getTestMethod()
            .map(method -> method.getName())
            .orElse("unknownMethod");
        
        // Create drive name: TestClass-testMethod
        // No colons allowed in drive names!
        currentTestDrive = testClassName + "-" + testMethodName;
        
        NamespacedRedisKeyService.setTestDriveOverride(currentTestDrive);
    }
    
    @AfterEach
    void cleanupTestIsolation() {
        // Clear the test drive override
        NamespacedRedisKeyService.clearTestDriveOverride();
        currentTestDrive = null;
    }
    
    /**
     * Get the current test drive name for use in test methods.
     * This returns the unique drive name for this test method.
     */
    protected String getTestDrive() {
        return currentTestDrive;
    }
}