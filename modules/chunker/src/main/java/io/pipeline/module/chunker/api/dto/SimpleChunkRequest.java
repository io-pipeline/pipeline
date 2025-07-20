package io.pipeline.module.chunker.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pipeline.module.chunker.model.ChunkingAlgorithm;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Simple chunking request for the REST API.
 * Used for basic text chunking without full PipeDoc complexity.
 */
@RegisterForReflection
public class SimpleChunkRequest {

    @JsonProperty("text")
    @Schema(description = "Text content to chunk", required = true, example = "This is a sample text that will be chunked into smaller pieces...")
    private String text;

    @JsonProperty("algorithm")
    @Schema(description = "Chunking algorithm to use", enumeration = {"character", "token", "sentence", "semantic"}, defaultValue = "token")
    private ChunkingAlgorithm algorithm = ChunkingAlgorithm.TOKEN;

    @JsonProperty("sourceField")
    @Schema(description = "Source field being chunked", defaultValue = "body", readOnly = true)
    private String sourceField = "body";

    @JsonProperty("chunkSize")
    @Schema(description = "Target character size for each chunk", minimum = "50", maximum = "10000", defaultValue = "500")
    private Integer chunkSize = 500;

    @JsonProperty("chunkOverlap")
    @Schema(description = "Character overlap between consecutive chunks", minimum = "0", maximum = "5000", defaultValue = "50")
    private Integer chunkOverlap = 50;

    @JsonProperty("preserveUrls")
    @Schema(description = "Whether to preserve URLs during chunking", defaultValue = "true")
    private Boolean preserveUrls = true;

    @JsonProperty("config_id")
    @Schema(description = "Auto-generated configuration identifier", readOnly = true, example = "overlap-body-500-50")
    private String configId;

    public SimpleChunkRequest() {}

    public SimpleChunkRequest(String text, Integer chunkSize, Integer chunkOverlap, Boolean preserveUrls) {
        this.text = text;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.preserveUrls = preserveUrls;
    }

    public SimpleChunkRequest(String text, ChunkingAlgorithm algorithm, String sourceField, Integer chunkSize, Integer chunkOverlap, Boolean preserveUrls) {
        this.text = text;
        this.algorithm = algorithm;
        this.sourceField = sourceField;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.preserveUrls = preserveUrls;
        this.configId = generateConfigId();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(Integer chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public Boolean getPreserveUrls() {
        return preserveUrls;
    }

    public void setPreserveUrls(Boolean preserveUrls) {
        this.preserveUrls = preserveUrls;
    }

    public ChunkingAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(ChunkingAlgorithm algorithm) {
        this.algorithm = algorithm;
        this.configId = generateConfigId(); // Regenerate when algorithm changes
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
        this.configId = generateConfigId(); // Regenerate when source field changes
    }

    public String getConfigId() {
        if (configId == null) {
            configId = generateConfigId();
        }
        return configId;
    }

    /**
     * Auto-generates the configuration ID based on algorithm and parameters.
     * Format: {algorithm}-{sourceField}-{chunkSize}-{chunkOverlap}
     * Example: "overlap-body-500-50"
     */
    private String generateConfigId() {
        return String.format("%s-%s-%d-%d", 
            algorithm != null ? algorithm.getValue() : "overlap",
            sourceField != null ? sourceField : "body",
            chunkSize != null ? chunkSize : 500,
            chunkOverlap != null ? chunkOverlap : 50
        );
    }
}