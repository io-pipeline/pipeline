package io.pipeline.validation.validators.field;

import io.pipeline.model.validation.validators.field.StringCorrectionUtil;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the StringCorrectionUtil class.
 */
public class StringCorrectionUtilTest {

    private static final Pattern STEP_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$");
    private static final Pattern ALPHANUMERIC_WITH_UNDERSCORES_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern ALPHA_START_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

    @Test
    public void testSuggestCorrection_NullOrEmpty() {
        // Test with null
        assertEquals("field-default", StringCorrectionUtil.suggestCorrection(null, STEP_NAME_PATTERN, "field"));
        
        // Test with empty string
        assertEquals("field-default", StringCorrectionUtil.suggestCorrection("", STEP_NAME_PATTERN, "field"));
    }
    
    @Test
    public void testSuggestCorrection_AlphanumericWithDashes() {
        // Test with valid string
        String valid = "valid-name-123";
        assertEquals(valid, StringCorrectionUtil.suggestCorrection(valid, STEP_NAME_PATTERN, "step"));
        
        // Test with invalid start character
        assertEquals("valid-name", StringCorrectionUtil.suggestCorrection("-valid-name", STEP_NAME_PATTERN, "step"));
        
        // Test with invalid end character
        assertEquals("valid-name", StringCorrectionUtil.suggestCorrection("valid-name-", STEP_NAME_PATTERN, "step"));
        
        // Test with invalid characters
        assertEquals("validname", StringCorrectionUtil.suggestCorrection("valid@name", STEP_NAME_PATTERN, "step"));
        
        // Test with multiple issues
        assertEquals("validname", StringCorrectionUtil.suggestCorrection("-valid@name-", STEP_NAME_PATTERN, "step"));
        
        // Test with all invalid characters
        assertEquals("step-default", StringCorrectionUtil.suggestCorrection("@#$%^&*", STEP_NAME_PATTERN, "step"));
    }
    
    @Test
    public void testSuggestCorrection_AlphanumericWithUnderscoresAndDashes() {
        // Test with valid string
        String valid = "valid_name-123";
        assertEquals(valid, StringCorrectionUtil.suggestCorrection(valid, ALPHANUMERIC_WITH_UNDERSCORES_PATTERN, "field"));
        
        // Test with invalid characters
        assertEquals("valid_name", StringCorrectionUtil.suggestCorrection("valid@name", ALPHANUMERIC_WITH_UNDERSCORES_PATTERN, "field"));
        
        // Test with all invalid characters
        assertEquals("field-default", StringCorrectionUtil.suggestCorrection("@#$%^&*", ALPHANUMERIC_WITH_UNDERSCORES_PATTERN, "field"));
    }
    
    @Test
    public void testSuggestCorrection_AlphaStartAlphanumericWithUnderscores() {
        // Test with valid string
        String valid = "validName_123";
        assertEquals(valid, StringCorrectionUtil.suggestCorrection(valid, ALPHA_START_PATTERN, "field"));
        
        // Test with invalid start character
        assertEquals("a1valid_name", StringCorrectionUtil.suggestCorrection("1valid_name", ALPHA_START_PATTERN, "field"));
        
        // Test with invalid characters
        assertEquals("validname", StringCorrectionUtil.suggestCorrection("valid-name", ALPHA_START_PATTERN, "field"));
        
        // Test with multiple issues
        assertEquals("a1validname", StringCorrectionUtil.suggestCorrection("1valid-name", ALPHA_START_PATTERN, "field"));
        
        // Test with all invalid characters
        assertEquals("field-default", StringCorrectionUtil.suggestCorrection("123-@#$", ALPHA_START_PATTERN, "field"));
    }
    
    @Test
    public void testSuggestCorrection_GenericPattern() {
        // Test with a custom pattern
        Pattern customPattern = Pattern.compile("^[A-Z][a-z]+$"); // Capitalized word
        
        // Test with valid string
        String valid = "Hello";
        assertEquals(valid, StringCorrectionUtil.suggestCorrection(valid, customPattern, "word"));
        
        // Test with invalid start character
        String result = StringCorrectionUtil.suggestCorrection("hello", customPattern, "word");
        assertTrue(customPattern.matcher(result).matches() || result.equals("hello"),
                "Result should either match the pattern or be unchanged: " + result);
        
        // Test with invalid characters
        result = StringCorrectionUtil.suggestCorrection("H3llo", customPattern, "word");
        assertTrue(customPattern.matcher(result).matches() || result.equals("H3llo"),
                "Result should either match the pattern or be unchanged: " + result);
    }
}