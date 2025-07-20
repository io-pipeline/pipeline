package io.pipeline.module.chunker.model;

/**
 * Enumeration of available chunking algorithms.
 * This enum defines the core chunking strategies that determine
 * how documents are split into chunks.
 */
public enum ChunkingAlgorithm {
    /**
     * Character-based chunking with sliding window approach.
     * Creates chunks with specified character size and overlap between consecutive chunks.
     */
    CHARACTER("character"),
    
    /**
     * Token-based chunking using tokenization.
     * Creates chunks based on token count rather than character count.
     */
    TOKEN("token"),
    
    /**
     * Sentence-boundary chunking using natural language processing.
     * Creates chunks by grouping complete sentences up to the target size.
     */
    SENTENCE("sentence"),
    
    /**
     * Semantic chunking using AI-based content understanding (future).
     * Creates chunks based on semantic boundaries and content coherence.
     */
    SEMANTIC("semantic");
    
    private final String value;
    
    ChunkingAlgorithm(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get algorithm from string value.
     * @param value The string representation
     * @return The corresponding algorithm
     * @throws IllegalArgumentException if value is not recognized
     */
    public static ChunkingAlgorithm fromValue(String value) {
        for (ChunkingAlgorithm algorithm : ChunkingAlgorithm.values()) {
            if (algorithm.value.equals(value)) {
                return algorithm;
            }
        }
        throw new IllegalArgumentException("Unknown chunking algorithm: " + value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}