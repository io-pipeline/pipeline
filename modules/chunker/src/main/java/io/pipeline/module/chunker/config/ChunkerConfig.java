package io.pipeline.module.chunker.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pipeline.module.chunker.model.ChunkingAlgorithm;
import io.pipeline.module.chunker.examples.SampleDocuments;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration record for chunker service.
 * This record serves as the single source of truth for chunker configuration schema.
 * The OpenAPI schema is auto-generated from this Java record.
 * This should generate
 */
@RegisterForReflection
@Schema(
    name = "ChunkerConfig", 
    description = "Configuration for text chunking operations with embedded sample documents for testing",
    examples = {
        SampleDocuments.US_CONSTITUTION_PREAMBLE,
        SampleDocuments.TECHNICAL_DOCUMENTATION,
        SampleDocuments.LITERARY_EXCERPT
    }
)
public record ChunkerConfig(
    
    @JsonProperty("algorithm")
    @Schema(
        description = "Chunking algorithm to use for splitting text", 
        enumeration = {"character", "token", "sentence", "semantic"}, 
        defaultValue = "token",
        required = true,
        examples = {"character", "sentence"}
    )
    @NotNull
    ChunkingAlgorithm algorithm,

    @JsonProperty("sourceField")
    @Schema(
        description = "Document field to extract text from for chunking", 
        defaultValue = "body",
        examples = {"body", "title", "metadata.summary", "content.text"}
    )
    String sourceField,

    @JsonProperty("chunkSize")
    @Schema(
        description = "Target size for each chunk (characters for 'character' algorithm, tokens for 'token' algorithm, etc.)", 
        minimum = "50", 
        maximum = "10000", 
        defaultValue = "500",
        required = true,
        examples = {"300", "500", "1000", "1500"}
    )
    @NotNull @Min(50) @Max(10000)
    Integer chunkSize,

    @JsonProperty("chunkOverlap")
    @Schema(
        description = "Amount of overlap between consecutive chunks (same units as chunkSize)", 
        minimum = "0", 
        maximum = "5000", 
        defaultValue = "50",
        examples = {"0", "25", "50", "100", "200"}
    )
    @Min(0) @Max(5000)
    Integer chunkOverlap,

    @JsonProperty("preserveUrls")
    @Schema(
        description = "Whether to preserve URLs as atomic units during chunking to maintain readability", 
        defaultValue = "true"
    )
    Boolean preserveUrls,

    @JsonProperty("cleanText")
    @Schema(
        description = "Whether to clean text by normalizing whitespace and line endings before chunking", 
        defaultValue = "true"
    )
    Boolean cleanText,

    @JsonProperty("config_id")
    @Schema(
        description = "Auto-generated configuration identifier based on algorithm and parameters", 
        readOnly = true,
        examples = {"character-body-500-50", "sentence-title-1000-100", "token-body-512-64"},
        extensions = @Extension(name = "x-hidden", value = "true")
    )
    String configId
) {
    
    // Default values as constants
    public static final ChunkingAlgorithm DEFAULT_ALGORITHM = ChunkingAlgorithm.TOKEN;
    public static final String DEFAULT_SOURCE_FIELD = "body";
    public static final Integer DEFAULT_CHUNK_SIZE = 500;
    public static final Integer DEFAULT_CHUNK_OVERLAP = 50;
    public static final Boolean DEFAULT_PRESERVE_URLS = true;
    public static final Boolean DEFAULT_CLEAN_TEXT = true;

    /**
     * Constructor with defaults and automatic config_id generation.
     */
    public ChunkerConfig(ChunkingAlgorithm algorithm, String sourceField, Integer chunkSize, Integer chunkOverlap, Boolean preserveUrls, Boolean cleanText) {
        this(
            algorithm != null ? algorithm : DEFAULT_ALGORITHM,
            sourceField != null ? sourceField : DEFAULT_SOURCE_FIELD,
            chunkSize != null ? chunkSize : DEFAULT_CHUNK_SIZE,
            chunkOverlap != null ? chunkOverlap : DEFAULT_CHUNK_OVERLAP,
            preserveUrls != null ? preserveUrls : DEFAULT_PRESERVE_URLS,
            cleanText != null ? cleanText : DEFAULT_CLEAN_TEXT,
            generateConfigId(
                algorithm != null ? algorithm : DEFAULT_ALGORITHM,
                sourceField != null ? sourceField : DEFAULT_SOURCE_FIELD,
                chunkSize != null ? chunkSize : DEFAULT_CHUNK_SIZE,
                chunkOverlap != null ? chunkOverlap : DEFAULT_CHUNK_OVERLAP
            )
        );
    }

    /**
     * Constructor with defaults and automatic config_id generation (backwards compatibility).
     */
    public ChunkerConfig(ChunkingAlgorithm algorithm, String sourceField, Integer chunkSize, Integer chunkOverlap, Boolean preserveUrls) {
        this(algorithm, sourceField, chunkSize, chunkOverlap, preserveUrls, DEFAULT_CLEAN_TEXT);
    }

    /**
     * Creates a default configuration with standard settings.
     */
    public static ChunkerConfig createDefault() {
        return new ChunkerConfig(
            DEFAULT_ALGORITHM,
            DEFAULT_SOURCE_FIELD,
            DEFAULT_CHUNK_SIZE,
            DEFAULT_CHUNK_OVERLAP,
            DEFAULT_PRESERVE_URLS,
            DEFAULT_CLEAN_TEXT
        );
    }

    /**
     * Creates a configuration from basic parameters with computed config_id.
     */
    public static ChunkerConfig create(ChunkingAlgorithm algorithm, String sourceField, Integer chunkSize, Integer chunkOverlap, Boolean preserveUrls) {
        return new ChunkerConfig(algorithm, sourceField, chunkSize, chunkOverlap, preserveUrls, DEFAULT_CLEAN_TEXT);
    }

    /**
     * Creates a configuration from basic parameters with computed config_id (full version).
     */
    public static ChunkerConfig create(ChunkingAlgorithm algorithm, String sourceField, Integer chunkSize, Integer chunkOverlap, Boolean preserveUrls, Boolean cleanText) {
        return new ChunkerConfig(algorithm, sourceField, chunkSize, chunkOverlap, preserveUrls, cleanText);
    }

    /**
     * Auto-generates the configuration ID based on algorithm and parameters.
     * Format: {algorithm}-{sourceField}-{chunkSize}-{chunkOverlap}
     * Examples: "character-body-500-50", "sentence-title-1000-100"
     */
    private static String generateConfigId(ChunkingAlgorithm algorithm, String sourceField, Integer chunkSize, Integer chunkOverlap) {
        return String.format("%s-%s-%d-%d", 
            algorithm.getValue(),
            sourceField,
            chunkSize,
            chunkOverlap
        );
    }

    /**
     * Validates the configuration and returns any validation errors.
     * @return null if valid, error message if invalid
     */
    public String validate() {
        if (chunkSize != null && (chunkSize < 50 || chunkSize > 10000)) {
            return "chunkSize must be between 50 and 10000";
        }
        if (chunkOverlap != null && (chunkOverlap < 0 || chunkOverlap > 5000)) {
            return "chunkOverlap must be between 0 and 5000";
        }
        if (chunkOverlap != null && chunkSize != null && chunkOverlap >= chunkSize) {
            return "chunkOverlap must be less than chunkSize";
        }
        if (algorithm == ChunkingAlgorithm.SEMANTIC) {
            return "Semantic chunking is not yet implemented";
        }
        return null; // No errors
    }

    /**
     * Returns true if this configuration is valid.
     */
    public boolean isValid() {
        return validate() == null;
    }

    /**
     * Gets the chunk size with appropriate units based on algorithm.
     */
    @Schema(
        description = "Human-readable description of chunk size with appropriate units based on the selected algorithm",
        readOnly = true,
        examples = {"500 tokens", "1000 characters", "3 sentences"}
    )
    public String getChunkSizeDescription() {
        return switch (algorithm) {
            case CHARACTER -> chunkSize + " characters";
            case TOKEN -> chunkSize + " tokens";
            case SENTENCE -> chunkSize + " sentences";
            case SEMANTIC -> chunkSize + " characters (semantic boundaries)";
        };
    }

    /**
     * Gets the overlap description with appropriate units based on algorithm.
     */
    @Schema(
        description = "Human-readable description of chunk overlap with appropriate units based on the selected algorithm",
        readOnly = true,
        examples = {"50 tokens", "100 characters", "1 sentence"}
    )
    public String getChunkOverlapDescription() {
        return switch (algorithm) {
            case CHARACTER -> chunkOverlap + " characters";
            case TOKEN -> chunkOverlap + " tokens";
            case SENTENCE -> chunkOverlap + " sentences";
            case SEMANTIC -> chunkOverlap + " characters (semantic overlap)";
        };
    }
}