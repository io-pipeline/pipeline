package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.config.service.PipelineConfigService;
import com.rokkon.pipeline.engine.validation.CompositeValidator;
import com.rokkon.pipeline.api.validation.ValidationResult;
import com.rokkon.pipeline.commons.validation.ValidationResultFactory;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import java.util.*;

/**
 * Service implementation for managing pipeline configurations in Consul KV store.
 * Provides CRUD operations with validation.
 */
@ApplicationScoped
public class PipelineConfigServiceImpl extends ConsulServiceBase implements PipelineConfigService {

    private static final Logger LOG = Logger.getLogger(PipelineConfigServiceImpl.class);

    @Inject
    CompositeValidator<PipelineConfig> validator;

    @Inject
    ClusterService clusterService;

    /**
     * Default constructor for CDI.
     */
    public PipelineConfigServiceImpl() {
        // Default constructor for CDI
    }

    /**
     * Creates a new pipeline configuration in Consul.
     */
    @CacheInvalidate(cacheName = "cluster-pipelines-list")
    @CacheInvalidate(cacheName = "cluster-pipelines")
    public Uni<ValidationResult> createPipeline(@CacheKey String clusterName, @CacheKey String pipelineId,
                                                PipelineConfig config) {
        LOG.infof("Creating pipeline '%s' in cluster '%s'", pipelineId, clusterName);

        // Validate the configuration
        ValidationResult validationResult = validator.validate(config);
        if (!validationResult.valid()) {
            return Uni.createFrom().item(validationResult);
        }

        // Check if pipeline already exists
        return getPipeline(clusterName, pipelineId)
                .flatMap(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(
                                ValidationResultFactory.failure(
                                        "Pipeline '" + pipelineId + "' already exists"));
                    }

                    return storePipelineInConsul(clusterName, pipelineId, config);
                });
    }

    /**
     * Updates an existing pipeline configuration.
     */
    @CacheInvalidate(cacheName = "cluster-pipelines-list")
    @CacheInvalidate(cacheName = "cluster-pipelines")
    public Uni<ValidationResult> updatePipeline(@CacheKey String clusterName, @CacheKey String pipelineId,
                                                PipelineConfig config) {
        LOG.infof("Updating pipeline '%s' in cluster '%s'", pipelineId, clusterName);

        // Validate the configuration
        ValidationResult validationResult = validator.validate(config);
        if (!validationResult.valid()) {
            return Uni.createFrom().item(validationResult);
        }

        // Check if pipeline exists
        return getPipeline(clusterName, pipelineId)
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        return Uni.createFrom().item(
                                ValidationResultFactory.failure(
                                        "Pipeline '" + pipelineId + "' not found"));
                    }

                    return updatePipelineInConsul(clusterName, pipelineId, config);
                });
    }

    /**
     * Deletes a pipeline configuration.
     */
    @CacheInvalidate(cacheName = "cluster-pipelines-list")
    @CacheInvalidate(cacheName = "cluster-pipelines")
    public Uni<ValidationResult> deletePipeline(@CacheKey String clusterName, @CacheKey String pipelineId) {
        LOG.infof("Deleting pipeline '%s' from cluster '%s'", pipelineId, clusterName);
        
        String key = buildPipelineKey(clusterName, pipelineId);
        return consulClient.getValue(key)
                .flatMap(existing -> {
                    if (existing == null || existing.getValue() == null) {
                        return Uni.createFrom().item(
                                ValidationResultFactory.failure(
                                        "Pipeline '" + pipelineId + "' not found"));
                    }

                    return consulClient.deleteValue(key)
                            .map(v -> {
                                LOG.infof("Successfully deleted pipeline '%s' from Consul", pipelineId);
                                return ValidationResultFactory.success();
                            });
                })
                .onFailure().recoverWithItem((Throwable error) -> {
                    LOG.errorf(error, "Error deleting pipeline: %s", pipelineId);
                    return ValidationResultFactory.failure("Error deleting pipeline: " + error.getMessage());
                });
    }

    /**
     * Retrieves a pipeline configuration.
     */
    @CacheResult(cacheName = "cluster-pipelines")
    public Uni<Optional<PipelineConfig>> getPipeline(@CacheKey String clusterName, @CacheKey String pipelineId) {
        String key = buildPipelineKey(clusterName, pipelineId);
        
        return consulClient.getValue(key)
                .map(keyValue -> {
                    if (keyValue == null || keyValue.getValue() == null) {
                        return Optional.<PipelineConfig>empty();
                    }
                    
                    try {
                        PipelineConfig config = objectMapper.readValue(keyValue.getValue(), PipelineConfig.class);
                        return Optional.of(config);
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to parse pipeline config for %s/%s", clusterName, pipelineId);
                        return Optional.<PipelineConfig>empty();
                    }
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.debugf("Failed to get pipeline %s/%s: %s", clusterName, pipelineId, error.getMessage());
                    return Optional.empty();
                });
    }

    /**
     * Lists all pipelines in a cluster.
     */
    @CacheResult(cacheName = "cluster-pipelines-list")
    public Uni<Map<String, PipelineConfig>> listPipelines(@CacheKey String clusterName) {
        String prefix = buildClusterPrefix(clusterName) + "pipelines/";
        LOG.debugf("Listing pipelines with prefix: %s", prefix);
        
        return consulClient.getKeys(prefix)
                .onItem().transformToUni(keys -> {
                    LOG.debugf("Found %d keys under prefix %s", keys != null ? keys.size() : 0, prefix);
                    
                    if (keys == null || keys.isEmpty()) {
                        return Uni.createFrom().item(new HashMap<String, PipelineConfig>());
                    }
                    
                    List<Uni<Map.Entry<String, PipelineConfig>>> unis = new ArrayList<>();
                    
                    for (String key : keys) {
                        String pipelineId = extractPipelineIdFromKey(key);
                        if (pipelineId != null && key.endsWith("/config")) {
                            unis.add(
                                getPipeline(clusterName, pipelineId)
                                    .map(configOpt -> configOpt
                                        .map(config -> Map.entry(pipelineId, config))
                                        .orElse(null))
                            );
                        }
                    }
                    
                    // Handle empty list case
                    if (unis.isEmpty()) {
                        return Uni.createFrom().item(new HashMap<String, PipelineConfig>());
                    }
                    
                    return Uni.combine().all().unis(unis)
                            .with(list -> {
                                Map<String, PipelineConfig> pipelines = new HashMap<>();
                                for (Object entry : list) {
                                    if (entry != null) {
                                        @SuppressWarnings("unchecked")
                                        Map.Entry<String, PipelineConfig> e = (Map.Entry<String, PipelineConfig>) entry;
                                        pipelines.put(e.getKey(), e.getValue());
                                    }
                                }
                                return pipelines;
                            });
                })
                .onFailure().recoverWithItem((Throwable error) -> {
                    LOG.errorf(error, "Failed to list pipelines for cluster: %s", clusterName);
                    return new HashMap<>();
                });
    }

    private Uni<ValidationResult> storePipelineInConsul(String clusterName, String pipelineId,
                                                        PipelineConfig config) {
        try {
            String configJson = objectMapper.writeValueAsString(config);
            String key = buildPipelineKey(clusterName, pipelineId);
            
            // Use CAS with index 0 to ensure we only create if it doesn't exist
            return createWithCas(key, configJson)
                    .map(success -> {
                        if (success) {
                            LOG.infof("Successfully created pipeline '%s' in Consul with CAS", pipelineId);
                            return ValidationResultFactory.success();
                        } else {
                            LOG.warnf("Failed to create pipeline - key may already exist: %s", key);
                            return ValidationResultFactory.failure("Pipeline already exists or CAS operation failed");
                        }
                    })
                    .onFailure().recoverWithItem((Throwable error) -> {
                        LOG.errorf(error, "Failed to store pipeline in Consul");
                        return ValidationResultFactory.failure("Failed to store pipeline: " + error.getMessage());
                    });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to serialize pipeline config");
            return Uni.createFrom().item(ValidationResultFactory.failure(
                    "Failed to serialize pipeline config: " + e.getMessage()));
        }
    }
    
    private Uni<ValidationResult> updatePipelineInConsul(String clusterName, String pipelineId,
                                                        PipelineConfig config) {
        String key = buildPipelineKey(clusterName, pipelineId);
        
        return updateWithCas(key, PipelineConfig.class, existingConfig -> config)
                .map(success -> {
                    if (success) {
                        LOG.infof("Successfully updated pipeline '%s' with CAS", pipelineId);
                        return ValidationResultFactory.success();
                    } else {
                        return ValidationResultFactory.failure(
                            "Failed to update pipeline - too many concurrent updates or pipeline not found");
                    }
                })
                .onFailure().recoverWithItem((Throwable error) -> {
                    LOG.errorf(error, "Failed to update pipeline: %s", pipelineId);
                    return ValidationResultFactory.failure("Failed to update pipeline: " + error.getMessage());
                });
    }


    private String buildPipelineKey(String clusterName, String pipelineId) {
        return kvPrefix + "/clusters/" + clusterName + "/pipelines/" + pipelineId + "/config";
    }

    private String buildClusterPrefix(String clusterName) {
        return kvPrefix + "/clusters/" + clusterName + "/";
    }

    private String extractPipelineIdFromKey(String key) {
        // Extract from: {kvPrefix}/clusters/{cluster}/pipelines/{pipelineId}/config
        String[] parts = key.split("/");
        if (parts.length >= 6 && "pipelines".equals(parts[3]) && "config".equals(parts[5])) {
            return parts[4];
        }
        return null;
    }
}
