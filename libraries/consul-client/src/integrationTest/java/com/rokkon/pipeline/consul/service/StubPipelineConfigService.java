package com.rokkon.pipeline.consul.service;

import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.api.validation.ValidationResult;
import com.rokkon.pipeline.commons.validation.ValidationResultFactory;
import io.smallrye.mutiny.Uni;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

/**
 * Stub implementation of PipelineConfigService for integration tests.
 * Returns empty results to allow ModuleWhitelistService tests to run.
 */
public class StubPipelineConfigService implements PipelineConfigService {
    
    @Override
    public Uni<ValidationResult> createPipeline(String clusterName, String pipelineId, PipelineConfig config) {
        // Not implemented for tests
        return Uni.createFrom().item(ValidationResultFactory.success());
    }
    
    @Override
    public Uni<ValidationResult> updatePipeline(String clusterName, String pipelineId, PipelineConfig config) {
        // Not implemented for tests
        return Uni.createFrom().item(ValidationResultFactory.success());
    }
    
    @Override
    public Uni<ValidationResult> deletePipeline(String clusterName, String pipelineId) {
        // Not implemented for tests
        return Uni.createFrom().item(ValidationResultFactory.success());
    }
    
    @Override
    public Uni<Optional<PipelineConfig>> getPipeline(String clusterName, String pipelineId) {
        // Return empty - no pipelines exist in test
        return Uni.createFrom().item(Optional.empty());
    }
    
    @Override
    public Uni<Map<String, PipelineConfig>> listPipelines(String clusterName) {
        // Return empty map - no pipelines exist in test
        return Uni.createFrom().item(new HashMap<>());
    }
}