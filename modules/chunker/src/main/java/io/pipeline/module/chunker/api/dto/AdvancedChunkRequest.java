package io.pipeline.module.chunker.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Map;

/**
 * Advanced chunking request for the REST API.
 * Supports full PipeDoc processing with complete configuration options.
 */
@RegisterForReflection
public class AdvancedChunkRequest {

    @JsonProperty("document")
    private DocumentDto document;

    @JsonProperty("options")
    private ChunkerOptionsDto options;

    @JsonProperty("metadata")
    private RequestMetadata metadata;

    public AdvancedChunkRequest() {}

    public AdvancedChunkRequest(DocumentDto document, ChunkerOptionsDto options, RequestMetadata metadata) {
        this.document = document;
        this.options = options;
        this.metadata = metadata;
    }

    public DocumentDto getDocument() {
        return document;
    }

    public void setDocument(DocumentDto document) {
        this.document = document;
    }

    public ChunkerOptionsDto getOptions() {
        return options;
    }

    public void setOptions(ChunkerOptionsDto options) {
        this.options = options;
    }

    public RequestMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(RequestMetadata metadata) {
        this.metadata = metadata;
    }

    @RegisterForReflection
    public static class DocumentDto {
        @JsonProperty("id")
        private String id;

        @JsonProperty("title")
        private String title;

        @JsonProperty("body")
        private String body;

        @JsonProperty("metadata")
        private Map<String, String> metadata;

        public DocumentDto() {}

        public DocumentDto(String id, String title, String body, Map<String, String> metadata) {
            this.id = id;
            this.title = title;
            this.body = body;
            this.metadata = metadata;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }

    @RegisterForReflection
    public static class ChunkerOptionsDto {
        @JsonProperty("sourceField")
        private String sourceField = "body";

        @JsonProperty("chunkSize")
        private Integer chunkSize = 500;

        @JsonProperty("chunkOverlap")
        private Integer chunkOverlap = 50;

        @JsonProperty("preserveUrls")
        private Boolean preserveUrls = true;

        @JsonProperty("chunkIdTemplate")
        private String chunkIdTemplate = "%s-%s-%d";

        public ChunkerOptionsDto() {}

        public String getSourceField() {
            return sourceField;
        }

        public void setSourceField(String sourceField) {
            this.sourceField = sourceField;
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

        public String getChunkIdTemplate() {
            return chunkIdTemplate;
        }

        public void setChunkIdTemplate(String chunkIdTemplate) {
            this.chunkIdTemplate = chunkIdTemplate;
        }
    }

    @RegisterForReflection
    public static class RequestMetadata {
        @JsonProperty("streamId")
        private String streamId;

        @JsonProperty("pipeStepName")
        private String pipeStepName;

        public RequestMetadata() {}

        public RequestMetadata(String streamId, String pipeStepName) {
            this.streamId = streamId;
            this.pipeStepName = pipeStepName;
        }

        public String getStreamId() {
            return streamId;
        }

        public void setStreamId(String streamId) {
            this.streamId = streamId;
        }

        public String getPipeStepName() {
            return pipeStepName;
        }

        public void setPipeStepName(String pipeStepName) {
            this.pipeStepName = pipeStepName;
        }
    }
}