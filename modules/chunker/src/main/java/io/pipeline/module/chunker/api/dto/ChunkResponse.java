package io.pipeline.module.chunker.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Response object for chunking operations.
 * Contains the generated chunks and metadata about the operation.
 */
@RegisterForReflection
public class ChunkResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("chunks")
    private List<ChunkDto> chunks;

    @JsonProperty("metadata")
    private ChunkingMetadata metadata;

    @JsonProperty("error")
    private String error;

    public ChunkResponse() {}

    public ChunkResponse(boolean success, List<ChunkDto> chunks, ChunkingMetadata metadata) {
        this.success = success;
        this.chunks = chunks;
        this.metadata = metadata;
    }

    public ChunkResponse(boolean success, String error) {
        this.success = success;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<ChunkDto> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkDto> chunks) {
        this.chunks = chunks;
    }

    public ChunkingMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ChunkingMetadata metadata) {
        this.metadata = metadata;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @RegisterForReflection
    public static class ChunkDto {
        @JsonProperty("id")
        private String id;

        @JsonProperty("text")
        private String text;

        @JsonProperty("startOffset")
        private int startOffset;

        @JsonProperty("endOffset")
        private int endOffset;

        public ChunkDto() {}

        public ChunkDto(String id, String text, int startOffset, int endOffset) {
            this.id = id;
            this.text = text;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public int getStartOffset() {
            return startOffset;
        }

        public void setStartOffset(int startOffset) {
            this.startOffset = startOffset;
        }

        public int getEndOffset() {
            return endOffset;
        }

        public void setEndOffset(int endOffset) {
            this.endOffset = endOffset;
        }
    }

    @RegisterForReflection
    public static class ChunkingMetadata {
        @JsonProperty("totalChunks")
        private int totalChunks;

        @JsonProperty("processingTimeMs")
        private long processingTimeMs;

        @JsonProperty("originalTextLength")
        private int originalTextLength;

        @JsonProperty("tokenizerUsed")
        private String tokenizerUsed;

        @JsonProperty("sentenceDetectorUsed")
        private String sentenceDetectorUsed;
        
        @JsonProperty("chunkerOptions")
        private Object chunkerOptions;

        public ChunkingMetadata() {}

        public ChunkingMetadata(int totalChunks, long processingTimeMs, int originalTextLength, 
                              String tokenizerUsed, String sentenceDetectorUsed) {
            this.totalChunks = totalChunks;
            this.processingTimeMs = processingTimeMs;
            this.originalTextLength = originalTextLength;
            this.tokenizerUsed = tokenizerUsed;
            this.sentenceDetectorUsed = sentenceDetectorUsed;
        }
        
        public ChunkingMetadata(int totalChunks, long processingTimeMs, int originalTextLength, 
                              String tokenizerUsed, String sentenceDetectorUsed, Object chunkerOptions) {
            this.totalChunks = totalChunks;
            this.processingTimeMs = processingTimeMs;
            this.originalTextLength = originalTextLength;
            this.tokenizerUsed = tokenizerUsed;
            this.sentenceDetectorUsed = sentenceDetectorUsed;
            this.chunkerOptions = chunkerOptions;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public void setTotalChunks(int totalChunks) {
            this.totalChunks = totalChunks;
        }

        public long getProcessingTimeMs() {
            return processingTimeMs;
        }

        public void setProcessingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }

        public int getOriginalTextLength() {
            return originalTextLength;
        }

        public void setOriginalTextLength(int originalTextLength) {
            this.originalTextLength = originalTextLength;
        }

        public String getTokenizerUsed() {
            return tokenizerUsed;
        }

        public void setTokenizerUsed(String tokenizerUsed) {
            this.tokenizerUsed = tokenizerUsed;
        }

        public String getSentenceDetectorUsed() {
            return sentenceDetectorUsed;
        }

        public void setSentenceDetectorUsed(String sentenceDetectorUsed) {
            this.sentenceDetectorUsed = sentenceDetectorUsed;
        }
        
        public Object getChunkerOptions() {
            return chunkerOptions;
        }
        
        public void setChunkerOptions(Object chunkerOptions) {
            this.chunkerOptions = chunkerOptions;
        }
    }
}