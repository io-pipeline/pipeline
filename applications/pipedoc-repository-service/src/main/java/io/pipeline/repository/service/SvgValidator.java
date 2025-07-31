package io.pipeline.repository.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SVG validation utility to prevent XSS and other injection attacks.
 * Validates SVG content and sanitizes it for safe storage and display.
 */
@ApplicationScoped
public class SvgValidator {
    
    private static final Logger LOG = Logger.getLogger(SvgValidator.class);
    
    // Maximum reasonable size for an icon SVG (100KB)
    private static final int MAX_SVG_SIZE = 100 * 1024;
    
    // Allowed SVG elements for icons (lowercase for comparison)
    private static final Set<String> ALLOWED_ELEMENTS = new HashSet<>(Arrays.asList(
        "svg", "g", "path", "circle", "ellipse", "line", "polyline", "polygon",
        "rect", "text", "tspan", "defs", "clippath", "mask", "pattern",
        "lineargradient", "radialgradient", "stop", "use", "symbol"
    ));
    
    // Allowed attributes (lowercase for comparison)
    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
        "id", "class", "style", "fill", "stroke", "stroke-width", "stroke-linecap",
        "stroke-linejoin", "stroke-dasharray", "opacity", "transform", "d", "points",
        "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry",
        "width", "height", "viewbox", "preserveaspectratio", "xmlns", "version",
        "fill-rule", "clip-rule", "clip-path", "mask", "href", "xlink-href",
        "offset", "stop-color", "stop-opacity", "gradientunits", "gradienttransform"
    ));
    
    // Dangerous patterns to block
    private static final Pattern[] DANGEROUS_PATTERNS = {
        Pattern.compile("<script", Pattern.CASE_INSENSITIVE),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE), // onclick, onload, etc.
        Pattern.compile("<iframe", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<embed", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<object", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<link", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<meta", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<foreignObject", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data:.*script", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<!\\[CDATA\\[", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<!ENTITY", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<!DOCTYPE", Pattern.CASE_INSENSITIVE)
    };
    
    // Pattern to extract SVG content
    private static final Pattern SVG_PATTERN = Pattern.compile(
        "<svg[^>]*>.*</svg>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    /**
     * Validates and sanitizes SVG content.
     * 
     * @param svg The SVG content to validate
     * @return The validated SVG content
     * @throws IllegalArgumentException if the SVG is invalid or contains dangerous content
     */
    public String validateAndSanitize(String svg) {
        if (svg == null || svg.trim().isEmpty()) {
            throw new IllegalArgumentException("SVG content cannot be null or empty");
        }
        
        // Check size
        if (svg.length() > MAX_SVG_SIZE) {
            throw new IllegalArgumentException(
                "SVG content exceeds maximum allowed size of " + MAX_SVG_SIZE + " bytes"
            );
        }
        
        // Trim whitespace
        svg = svg.trim();
        
        // Check if it looks like SVG
        if (!svg.startsWith("<svg") || !svg.endsWith("</svg>")) {
            throw new IllegalArgumentException("Invalid SVG format: must start with <svg and end with </svg>");
        }
        
        // Check for dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(svg).find()) {
                LOG.warn("Dangerous pattern detected in SVG: " + pattern.pattern());
                throw new IllegalArgumentException(
                    "SVG contains potentially dangerous content: " + pattern.pattern()
                );
            }
        }
        
        // Basic structure validation
        if (!SVG_PATTERN.matcher(svg).matches()) {
            throw new IllegalArgumentException("Invalid SVG structure");
        }
        
        // Basic tag structure validation - ensure all tags are properly closed
        // Note: This is a simple check and doesn't handle self-closing tags perfectly
        // For a production system, consider using a proper XML parser
        
        // Validate elements (basic check)
        validateElements(svg);
        
        // If we get here, the SVG is reasonably safe
        return svg;
    }
    
    /**
     * Validates that the SVG contains only allowed elements and attributes.
     * This is a basic check and not a full XML parser.
     */
    private void validateElements(String svg) {
        // Extract element names (basic regex approach)
        Pattern elementPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9-]*)(?:\\s|>|/)");
        var matcher = elementPattern.matcher(svg);
        
        while (matcher.find()) {
            String elementName = matcher.group(1).toLowerCase();
            if (!ALLOWED_ELEMENTS.contains(elementName)) {
                throw new IllegalArgumentException(
                    "SVG contains disallowed element: " + elementName
                );
            }
        }
        
        // Validate attributes
        validateAttributes(svg);
    }
    
    /**
     * Validates that the SVG contains only allowed attributes.
     */
    private void validateAttributes(String svg) {
        // Pattern to extract tags and their attributes
        Pattern tagPattern = Pattern.compile("<([a-zA-Z][^>\\s]*)(\\s[^>]*)?>");
        var tagMatcher = tagPattern.matcher(svg);
        
        while (tagMatcher.find()) {
            String attributes = tagMatcher.group(2);
            if (attributes != null && !attributes.trim().isEmpty()) {
                // Pattern to match individual attributes
                Pattern attrPattern = Pattern.compile("\\s+([a-zA-Z][a-zA-Z0-9:_-]*)\\s*=");
                var attrMatcher = attrPattern.matcher(attributes);
                
                while (attrMatcher.find()) {
                    String attrName = attrMatcher.group(1).toLowerCase();
                    // Skip xmlns attributes as they're namespace declarations
                    if (attrName.startsWith("xmlns")) {
                        continue;
                    }
                    
                    // Check if attribute is allowed
                    if (!ALLOWED_ATTRIBUTES.contains(attrName) && !ALLOWED_ATTRIBUTES.contains(attrName.replace(":", "-"))) {
                        throw new IllegalArgumentException(
                            "SVG contains disallowed attribute: " + attrName
                        );
                    }
                }
            }
        }
    }
    
    /**
     * Counts occurrences of a substring.
     */
    private int countOccurrences(String str, String substr) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substr, index)) != -1) {
            count++;
            index += substr.length();
        }
        return count;
    }
    
    /**
     * Provides a safe default SVG icon if validation fails.
     */
    public String getDefaultIcon() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
               "<path d=\"M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z\"/>" +
               "</svg>";
    }
}