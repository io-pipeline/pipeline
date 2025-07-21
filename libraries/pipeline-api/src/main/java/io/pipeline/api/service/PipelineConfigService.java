package io.pipeline.api.service;

import io.pipeline.api.validation.ValidationResult;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
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
    
    // Convenience methods for pipeline step access
    
    /**
     * Retrieves a specific step from a pipeline.
     * 
     * @param clusterName The cluster name
     * @param pipelineId The pipeline ID  
     * @param stepId The step ID
     * @return The step configuration if found
     */
    Uni<Optional<PipelineStepConfig>> getPipeStep(String clusterName, String pipelineId, String stepId);
    
    /**
     * Searches all pipelines in a cluster for a step with the given ID.
     * Useful for global step references using the pipeline:step naming convention.
     * 
     * @param clusterName The cluster name
     * @param stepId The step ID to search for
     * @return The step configuration if found in any pipeline
     */
    Uni<Optional<PipelineStepConfig>> findPipeStep(String clusterName, String stepId);
    
    /**
     * Finds which pipeline contains a step with the given ID.
     * Reverse lookup for step-to-pipeline mapping.
     * 
     * @param clusterName The cluster name
     * @param stepId The step ID to search for
     * @return The pipeline ID that contains the step, if found
     */
    Uni<Optional<String>> getPipelineForStep(String clusterName, String stepId);
}