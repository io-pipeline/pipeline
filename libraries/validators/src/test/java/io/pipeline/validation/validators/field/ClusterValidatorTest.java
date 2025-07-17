package io.pipeline.validation.validators.field;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.model.validation.validators.field.ClusterValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the ClusterValidator class.
 */
public class ClusterValidatorTest {

    private ClusterValidator validator;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        validator = new ClusterValidator(objectMapper);
    }
    
    @Test
    public void testCanHandle() {
        // Should handle PipelineConfig
        assertTrue(validator.canHandle(new PipelineConfig("test", Collections.emptyMap())));
        
        // Should not handle other types
        assertFalse(validator.canHandle(new TestValidatable()));
    }
    
    @Test
    public void testGetPriority() {
        // Should have the expected priority
        assertEquals(100, validator.getPriority());
    }
    
    // Simple implementation of PipelineConfigValidatable for testing
    private static class TestValidatable implements PipelineConfigValidatable {
    }
}