package io.pipeline.api.service;

import io.pipeline.api.validation.ValidationResult;
import io.pipeline.api.model.PipelineConfig;
import io.smallrye.mutiny.Uni;

import java.util.Map;
import java.util.Optional;

/**
 * Service interface for managing pipeline configurations in Consul KV store.
 * Provides CRUD operations with validation.
 */
public interface PipelineConfigService {
    
    /**
     * Creates a new pipeline configuration in Consul.
     */
    Uni<ValidationResult> createPipeline(String clusterName, String pipelineId, PipelineConfig config);
    
    /**
     * Updates an existing pipeline configuration.
     */
    Uni<ValidationResult> updatePipeline(String clusterName, String pipelineId, PipelineConfig config);
    
    /**
     * Deletes a pipeline configuration.
     */
    Uni<ValidationResult> deletePipeline(String clusterName, String pipelineId);
    
    /**
     * Retrieves a pipeline configuration.
     */
    Uni<Optional<PipelineConfig>> getPipeline(String clusterName, String pipelineId);
    
    /**
     * Lists all pipelines in a cluster.
     */
    Uni<Map<String, PipelineConfig>> listPipelines(String clusterName);
}