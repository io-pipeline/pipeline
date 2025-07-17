package io.pipeline.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collections;
import java.util.Map;

/**
 * A catalog of available pipeline module configurations.
 * Each entry maps a module's implementationId to its definition.
 * This record is immutable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Catalog of available pipeline modules")
public record PipelineModuleMap(
        @JsonProperty("availableModules") Map<String, PipelineModuleConfiguration> availableModules
) {
    public PipelineModuleMap {
        availableModules = (availableModules == null) ? Collections.emptyMap() : Map.copyOf(availableModules);
        // Map.copyOf will throw NPE if map contains null keys or values.
    }
}