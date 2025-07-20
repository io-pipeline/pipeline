package io.pipeline.module.chunker.demo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Metadata for demo documents available for chunking.
 * This record represents document information loaded from the CSV file.
 */
@RegisterForReflection
@Schema(name = "DocumentMetadata", description = "Metadata for demo documents")
public record DocumentMetadata(
    
    @JsonProperty("filename")
    @Schema(description = "Name of the text file", example = "sample_article.txt")
    String filename,
    
    @JsonProperty("title")
    @Schema(description = "Human-readable title of the document", example = "Sample Technology Article")
    String title,
    
    @JsonProperty("author")
    @Schema(description = "Author of the document", example = "Tech Writer")
    String author,
    
    @JsonProperty("category")
    @Schema(description = "Category or genre of the document", example = "Technology")
    String category,
    
    @JsonProperty("description")
    @Schema(description = "Brief description of the document content")
    String description,
    
    @JsonProperty("estimated_chunks")
    @Schema(description = "Estimated number of chunks this document will produce", example = "15")
    Integer estimatedChunks,
    
    @JsonProperty("recommended_chunk_size")
    @Schema(description = "Recommended chunk size for optimal results", example = "400")
    Integer recommendedChunkSize,
    
    @JsonProperty("recommended_algorithm") 
    @Schema(description = "Recommended chunking algorithm", enumeration = {"character", "token", "sentence", "semantic"}, example = "token")
    String recommendedAlgorithm,
    
    @JsonProperty("file_size")
    @Schema(description = "Size of the text file in bytes", readOnly = true)
    Long fileSize,
    
    @JsonProperty("preview")
    @Schema(description = "First 200 characters of the document for preview", readOnly = true)
    String preview
    
) {
    
    /**
     * Creates a DocumentMetadata with calculated file size and preview.
     */
    public static DocumentMetadata create(
            String filename, String title, String author, String category, 
            String description, Integer estimatedChunks, Integer recommendedChunkSize, 
            String recommendedAlgorithm, Long fileSize, String preview) {
        return new DocumentMetadata(
            filename, title, author, category, description, 
            estimatedChunks, recommendedChunkSize, recommendedAlgorithm,
            fileSize, preview
        );
    }
    
    /**
     * Creates a DocumentMetadata from CSV data without file size and preview.
     */
    public static DocumentMetadata fromCsv(
            String filename, String title, String author, String category,
            String description, Integer estimatedChunks, Integer recommendedChunkSize,
            String recommendedAlgorithm) {
        return new DocumentMetadata(
            filename, title, author, category, description,
            estimatedChunks, recommendedChunkSize, recommendedAlgorithm,
            null, null
        );
    }
    
    /**
     * Returns a DocumentMetadata with updated file size and preview.
     */
    public DocumentMetadata withFileInfo(Long fileSize, String preview) {
        return new DocumentMetadata(
            filename, title, author, category, description,
            estimatedChunks, recommendedChunkSize, recommendedAlgorithm,
            fileSize, preview
        );
    }
    
    /**
     * Gets a safe preview text, limiting to 200 characters.
     */
    public String getSafePreview() {
        if (preview == null || preview.isEmpty()) {
            return "No preview available";
        }
        return preview.length() > 200 ? preview.substring(0, 200) + "..." : preview;
    }
    
    /**
     * Validates that this document metadata has all required fields.
     */
    public boolean isValid() {
        return filename != null && !filename.isEmpty() &&
               title != null && !title.isEmpty() &&
               recommendedAlgorithm != null && !recommendedAlgorithm.isEmpty();
    }
}