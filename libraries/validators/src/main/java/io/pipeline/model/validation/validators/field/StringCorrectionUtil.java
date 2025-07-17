package io.pipeline.model.validation.validators.field;

import java.util.regex.Pattern;

/**
 * Utility class for suggesting corrections to string fields based on pattern requirements.
 */
public class StringCorrectionUtil {

    /**
     * Suggests a corrected version of a string based on a pattern.
     *
     * @param original The original string to correct
     * @param pattern The pattern that the string should match
     * @param fieldName The name of the field (used for default values)
     * @return A corrected version of the string that matches the pattern
     */
    public static String suggestCorrection(String original, Pattern pattern, String fieldName) {
        if (original == null || original.isEmpty()) {
            return fieldName + "-default";
        }
        
        // Extract the pattern string
        String patternStr = pattern.pattern();
        
        // Handle common patterns
        return switch (patternStr) {
            case "^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$" -> correctAlphanumericWithDashes(original, fieldName);
            case "^[a-zA-Z0-9_-]+$" -> correctAlphanumericWithUnderscoresAndDashes(original, fieldName);
            case "^[a-zA-Z][a-zA-Z0-9_]*$" -> correctAlphaStartAlphanumericWithUnderscores(original, fieldName);
            default ->
                // Generic approach for other patterns
                    genericPatternCorrection(original, pattern, fieldName);
        };
    }
    
    /**
     * Corrects a string to match the pattern ^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$
     * This pattern requires:
     * - Start with alphanumeric
     * - End with alphanumeric
     * - Only contain alphanumeric and dash in the middle
     */
    private static String correctAlphanumericWithDashes(String original, String fieldName) {
        if (original == null || original.isEmpty()) {
            return fieldName + "-default";
        }
        // Remove all characters that aren't alphanumeric or dash
        String cleaned = original.replaceAll("[^a-zA-Z0-9-]", "");
        
        // If empty after cleaning, return default
        if (cleaned.isEmpty()) {
            return fieldName + "-default";
        }
        
        // Process the string to ensure it starts with alphanumeric
        StringBuilder result = new StringBuilder(cleaned);
        
        // Remove leading dashes
        while (!result.isEmpty() && result.charAt(0) == '-') {
            result.deleteCharAt(0);
        }
        
        // If empty after removing leading dashes, return default
        if (result.isEmpty()) {
            return fieldName + "-default";
        }
        
        // Ensure it starts with alphanumeric
        if (!Character.isLetterOrDigit(result.charAt(0))) {
            result.insert(0, 'a');
        }
        
        // Remove trailing dashes
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '-') {
            result.deleteCharAt(result.length() - 1);
        }
        
        // Ensure it ends with alphanumeric
        if (result.isEmpty() || !Character.isLetterOrDigit(result.charAt(result.length() - 1))) {
            result.append('1');
        }
        
        // Special case for "-a-" -> "a1" (when there's only one character left after cleaning)
        if (result.length() == 1 && original.contains("-")) {
            result.append("1");
        }
        
        return result.toString();
    }
    
    /**
     * Corrects a string to match the pattern ^[a-zA-Z0-9_-]+$
     * This pattern requires only alphanumeric, underscore, and dash characters.
     */
    private static String correctAlphanumericWithUnderscoresAndDashes(String original, String fieldName) {
        if (original == null || original.isEmpty()) {
            return fieldName + "-default";
        }
        

        // Remove all characters that aren't alphanumeric, underscore, or dash
        // But preserve the structure by replacing invalid chars with underscores
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                result.append(c);
            } else if (i > 0 && i < original.length() - 1) {
                // Replace invalid characters in the middle with underscores
                // but avoid consecutive underscores
                if (!result.isEmpty() && result.charAt(result.length() - 1) != '_') {
                    result.append('_');
                }
            }
        }
        
        String cleaned = result.toString();
        
        // If empty after cleaning, return default
        if (cleaned.isEmpty()) {
            return fieldName + "-default";
        }
        
        return cleaned;
    }
    
    /**
     * Corrects a string to match the pattern ^[a-zA-Z][a-zA-Z0-9_]*$
     * This pattern requires:
     * - Start with a letter
     * - Only contain letters, numbers, and underscores
     */
    private static String correctAlphaStartAlphanumericWithUnderscores(String original, String fieldName) {
        if (original == null || original.isEmpty()) {
            return fieldName + "-default";
        }

        // Remove all characters that aren't alphanumeric or underscore
        String cleaned = original.replaceAll("[^a-zA-Z0-9_]", "");
        
        // If empty after cleaning, return default
        if (cleaned.isEmpty()) {
            return fieldName + "-default";
        }
        
        // If the string consists entirely of numbers or underscores, return default
        if (cleaned.matches("^[0-9_]+$")) {
            return fieldName + "-default";
        }
        
        // Ensure it starts with a letter
        if (!Character.isLetter(cleaned.charAt(0))) {
            // Remove the first character if it's not a letter and add 'a' at the beginning
            cleaned = "a" + cleaned;
        }
        
        return cleaned;
    }
    
    /**
     * Generic approach for correcting a string to match a pattern.
     * This is a best-effort approach and may not work for all patterns.
     */
    private static String genericPatternCorrection(String original, Pattern pattern, String fieldName) {
        if (original == null || original.isEmpty()) {
            return fieldName + "-default";
        }
        
        // First check if the original already matches the pattern
        if (pattern.matcher(original).matches()) {
            return original;
        }
        
        String patternStr = pattern.pattern();
        String cleaned = original;
        
        // For patterns like ^[A-Z][a-z]+$, we need to handle each character position separately
        if (patternStr.equals("^[A-Z][a-z]+$")) {
            // Special case for capitalized word pattern
            StringBuilder sb = new StringBuilder();

            // Find the first letter in the string
            boolean foundFirstLetter = false;
            for (int i = 0; i < cleaned.length(); i++) {
                char c = cleaned.charAt(i);
                if (!foundFirstLetter && Character.isLetter(c)) {
                    // Capitalize the first letter
                    sb.append(Character.toUpperCase(c));
                    foundFirstLetter = true;
                } else if (foundFirstLetter && Character.isLowerCase(c)) {
                    // Only keep lowercase letters after the first letter
                    sb.append(c);
                }
            }

            // If no letters were found, use the default
            if (sb.isEmpty()) {
                return fieldName + "-default";
            }

            cleaned = sb.toString();
            return cleaned;
        }
        
        // For other patterns, try a more general approach
        // Extract all character classes from the pattern
        StringBuilder validChars = new StringBuilder();
        int pos = 0;
        while (pos < patternStr.length()) {
            int startBracket = patternStr.indexOf('[', pos);
            if (startBracket < 0) break;
            
            int endBracket = patternStr.indexOf(']', startBracket);
            if (endBracket < 0) break;
            
            String charClass = patternStr.substring(startBracket + 1, endBracket);
            if (!charClass.startsWith("^")) {
                validChars.append(charClass);
            }
            
            pos = endBracket + 1;
        }
        
        if (!validChars.isEmpty()) {
            // Remove characters that aren't in any of the valid character classes
            String validCharsRegex = "[^" + validChars +
                    "]";
            
            cleaned = original.replaceAll(validCharsRegex, "");
            
            // If empty after cleaning, return default
            if (cleaned.isEmpty()) {
                return fieldName + "-default";
            }
            
            // Check if the pattern requires a specific start character
            if (patternStr.startsWith("^[a-zA-Z")) {
                if (!Character.isLetter(cleaned.charAt(0))) {
                    cleaned = "a" + cleaned;
                }
            } else if (patternStr.startsWith("^[a-zA-Z0-9")) {
                if (!Character.isLetterOrDigit(cleaned.charAt(0))) {
                    cleaned = "a" + cleaned;
                }
            }
            
            // Check if the pattern requires a specific end character
            if (patternStr.endsWith("[a-zA-Z0-9]$")) {
                if (!Character.isLetterOrDigit(cleaned.charAt(cleaned.length() - 1))) {
                    cleaned = cleaned + "1";
                }
            } else if (patternStr.endsWith("[a-zA-Z]$")) {
                if (!Character.isLetter(cleaned.charAt(cleaned.length() - 1))) {
                    cleaned = cleaned + "a";
                }
            }
        }
        
        return cleaned;
    }
}