package io.pipeline.repository.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class SvgValidatorTest {
    
    @Inject
    SvgValidator svgValidator;
    
    @Test
    void testValidSvg() {
        // Given - Valid SVG
        String validSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
                         "<path d=\"M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10z\"/>" +
                         "</svg>";
        
        // When
        String result = svgValidator.validateAndSanitize(validSvg);
        
        // Then
        assertThat("Valid SVG should be returned unchanged", result, is(equalTo(validSvg)));
    }
    
    @Test
    void testSvgWithScriptTag() {
        // Given - SVG with script tag
        String maliciousSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                             "<script>alert('XSS')</script>" +
                             "<circle cx=\"50\" cy=\"50\" r=\"40\"/>" +
                             "</svg>";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(maliciousSvg)
        );
        
        assertThat("Exception message should mention dangerous content",
            exception.getMessage(), containsString("dangerous content"));
    }
    
    @Test
    void testSvgWithJavaScriptUrl() {
        // Given - SVG with javascript: URL
        String maliciousSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                             "<a href=\"javascript:alert('XSS')\">" +
                             "<circle cx=\"50\" cy=\"50\" r=\"40\"/>" +
                             "</a></svg>";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(maliciousSvg)
        );
        
        assertThat("Exception should be thrown for javascript: URLs",
            exception.getMessage(), containsString("dangerous content"));
    }
    
    @Test
    void testSvgWithOnClickHandler() {
        // Given - SVG with onclick handler
        String maliciousSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                             "<circle cx=\"50\" cy=\"50\" r=\"40\" onclick=\"alert('XSS')\"/>" +
                             "</svg>";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(maliciousSvg)
        );
        
        assertThat("Exception should be thrown for event handlers",
            exception.getMessage(), containsString("dangerous content"));
    }
    
    @Test
    void testSvgWithForeignObject() {
        // Given - SVG with foreignObject
        String maliciousSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                             "<foreignObject><iframe src=\"evil.com\"/></foreignObject>" +
                             "</svg>";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(maliciousSvg)
        );
        
        assertThat("Exception should be thrown for foreignObject",
            exception.getMessage(), containsString("dangerous content"));
    }
    
    @Test
    void testSvgWithDisallowedElement() {
        // Given - SVG with disallowed element
        String invalidSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                           "<animateTransform attributeName=\"transform\" type=\"rotate\"/>" +
                           "</svg>";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(invalidSvg)
        );
        
        assertThat("Exception should mention disallowed element",
            exception.getMessage(), containsString("disallowed element"));
    }
    
    @Test
    void testSvgTooLarge() {
        // Given - SVG that's too large
        StringBuilder largeSvg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\">");
        for (int i = 0; i < 10000; i++) {
            largeSvg.append("<path d=\"M 0 0 L 100 100 L 200 200 Z\"/>");
        }
        largeSvg.append("</svg>");
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(largeSvg.toString())
        );
        
        assertThat("Exception should mention size limit",
            exception.getMessage(), containsString("exceeds maximum allowed size"));
    }
    
    @Test
    void testNullOrEmptySvg() {
        // Test null
        IllegalArgumentException nullException = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(null)
        );
        assertThat("Exception for null SVG",
            nullException.getMessage(), containsString("cannot be null or empty"));
        
        // Test empty
        IllegalArgumentException emptyException = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize("")
        );
        assertThat("Exception for empty SVG",
            emptyException.getMessage(), containsString("cannot be null or empty"));
        
        // Test whitespace only
        IllegalArgumentException wsException = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize("   \n\t  ")
        );
        assertThat("Exception for whitespace-only SVG",
            wsException.getMessage(), containsString("cannot be null or empty"));
    }
    
    @Test
    void testInvalidSvgFormat() {
        // Given - Not starting with <svg
        String notSvg = "<div>Not an SVG</div>";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(notSvg)
        );
        
        assertThat("Exception should mention invalid format",
            exception.getMessage(), containsString("Invalid SVG format"));
    }
    
    @Test
    void testSelfClosingTags() {
        // Given - SVG with self-closing tags (which is valid)
        String validSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                         "<circle cx=\"50\" cy=\"50\" r=\"40\"/>" +
                         "<rect x=\"10\" y=\"10\" width=\"30\" height=\"30\"/>" +
                         "</svg>";
        
        // When
        String result = svgValidator.validateAndSanitize(validSvg);
        
        // Then
        assertThat("Self-closing tags should be valid", result, is(equalTo(validSvg)));
    }
    
    @Test
    void testDefaultIcon() {
        // When
        String defaultIcon = svgValidator.getDefaultIcon();
        
        // Then
        assertThat("Default icon should not be null", defaultIcon, is(notNullValue()));
        assertThat("Default icon should be valid SVG", defaultIcon, startsWith("<svg"));
        assertThat("Default icon should end with </svg>", defaultIcon, endsWith("</svg>"));
        
        // Verify default icon is itself valid
        String validated = svgValidator.validateAndSanitize(defaultIcon);
        assertThat("Default icon should pass validation", validated, is(equalTo(defaultIcon)));
    }
    
    @Test
    void testSvgWithDisallowedAttribute() {
        // Given - SVG with disallowed attribute
        String invalidSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                           "<circle cx=\"50\" cy=\"50\" r=\"40\" data-custom=\"test\"/>" +
                           "</svg>";
        
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> svgValidator.validateAndSanitize(invalidSvg)
        );
        
        assertThat("Exception should mention disallowed attribute",
            exception.getMessage(), containsString("disallowed attribute"));
    }
    
    @Test
    void testComplexValidSvg() {
        // Given - Complex but valid SVG with gradients
        String complexSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">" +
                           "<defs>" +
                           "<linearGradient id=\"grad1\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"0%\">" +
                           "<stop offset=\"0%\" stop-color=\"rgb(255,255,0)\" stop-opacity=\"1\" />" +
                           "<stop offset=\"100%\" stop-color=\"rgb(255,0,0)\" stop-opacity=\"1\" />" +
                           "</linearGradient>" +
                           "</defs>" +
                           "<circle cx=\"50\" cy=\"50\" r=\"40\" fill=\"url(#grad1)\" />" +
                           "</svg>";
        
        // When
        String result = svgValidator.validateAndSanitize(complexSvg);
        
        // Then
        assertThat("Complex valid SVG should be returned unchanged", result, is(equalTo(complexSvg)));
    }
}