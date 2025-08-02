package io.pipeline.module.opensearchsink.config;

import java.util.Objects;

/**
 * Configuration for embedding fields with different dimensions.
 * Supports the strategy of creating separate nested fields for different vector dimensions
 * (e.g., embeddings_384, embeddings_768) as discussed in the implementation answers.
 */
public class EmbeddingFieldConfig {
    
    private final String fieldName;
    private final int dimension;
    private final String embeddingId;

    public EmbeddingFieldConfig(String fieldName, int dimension, String embeddingId) {
        this.fieldName = fieldName;
        this.dimension = dimension;
        this.embeddingId = embeddingId;
    }

    /**
     * Creates a field name that includes dimension to avoid conflicts.
     * Format: embeddings_{dimension} or embeddings_{chunkerId}_{embeddingId}
     */
    public static String createDimensionSpecificFieldName(String baseFieldName, int dimension) {
        return baseFieldName + "_" + dimension;
    }

    /**
     * Creates a field name that includes both chunker and embedding IDs for maximum specificity.
     * Format: embeddings_{chunkerRunId}_{embeddingRunId}
     */
    public static String createRunSpecificFieldName(String baseFieldName, String chunkerRunId, String embeddingRunId) {
        return baseFieldName + "_" + sanitizeId(chunkerRunId) + "_" + sanitizeId(embeddingRunId);
    }

    private static String sanitizeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    public String getFieldName() {
        return fieldName;
    }

    public int getDimension() {
        return dimension;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingFieldConfig that = (EmbeddingFieldConfig) o;
        return dimension == that.dimension && 
               Objects.equals(fieldName, that.fieldName) && 
               Objects.equals(embeddingId, that.embeddingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, dimension, embeddingId);
    }

    @Override
    public String toString() {
        return "EmbeddingFieldConfig{" +
                "fieldName='" + fieldName + '\'' +
                ", dimension=" + dimension +
                ", embeddingId='" + embeddingId + '\'' +
                '}';
    }
}