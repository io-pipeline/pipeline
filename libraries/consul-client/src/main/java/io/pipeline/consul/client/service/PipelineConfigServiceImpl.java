package io.pipeline.consul.client.service;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.service.ClusterService;
import io.pipeline.api.service.PipelineConfigService;
import io.pipeline.model.validation.CompositeValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
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
                    LOG.debugf("DEBUG: Found %d keys under prefix %s", keys != null ? keys.size() : 0, prefix);
                    if (keys != null) {
                        for (String key : keys) {
                            LOG.debugf("DEBUG: Found key: '%s'", key);
                        }
                    }
                    
                    if (keys == null || keys.isEmpty()) {
                        return Uni.createFrom().item(new HashMap<String, PipelineConfig>());
                    }
                    
                    List<Uni<Map.Entry<String, PipelineConfig>>> unis = new ArrayList<>();
                    
                    for (String key : keys) {
                        String pipelineId = extractPipelineIdFromKey(key);
                        LOG.debugf("DEBUG: For key '%s', extracted pipelineId: '%s'", key, pipelineId);
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
        LOG.debugf("DEBUG: extractPipelineIdFromKey input key: '%s'", key);
        String[] parts = key.split("/");
        LOG.debugf("DEBUG: split into %d parts: %s", parts.length, java.util.Arrays.toString(parts));
        
        if (parts.length >= 6) {
            LOG.debugf("DEBUG: parts[3]='%s' (expecting 'pipelines'), parts[5]='%s' (expecting 'config')", 
                      parts[3], parts[5]);
        }
        
        if (parts.length >= 6 && "pipelines".equals(parts[3]) && "config".equals(parts[5])) {
            LOG.debugf("DEBUG: extracted pipelineId: '%s'", parts[4]);
            return parts[4];
        }
        LOG.debugf("DEBUG: failed to extract pipelineId from key: '%s'", key);
        return null;
    }

    // Convenience methods for pipeline step access

    /**
     * Retrieves a specific step from a pipeline.
     */
    public Uni<Optional<PipelineStepConfig>> getPipeStep(String clusterName, String pipelineId, String stepId) {
        LOG.debugf("Getting step '%s' from pipeline '%s' in cluster '%s'", stepId, pipelineId, clusterName);
        
        return getPipeline(clusterName, pipelineId)
                .map(pipelineOpt -> {
                    if (pipelineOpt.isEmpty()) {
                        LOG.debugf("Pipeline '%s' not found in cluster '%s'", pipelineId, clusterName);
                        return Optional.<PipelineStepConfig>empty();
                    }
                    
                    PipelineConfig pipeline = pipelineOpt.get();
                    if (pipeline.pipelineSteps() == null) {
                        LOG.debugf("Pipeline '%s' has no steps", pipelineId);
                        return Optional.<PipelineStepConfig>empty();
                    }
                    
                    PipelineStepConfig step = pipeline.pipelineSteps().get(stepId);
                    if (step != null) {
                        LOG.debugf("Found step '%s' in pipeline '%s'", stepId, pipelineId);
                        return Optional.of(step);
                    } else {
                        LOG.debugf("Step '%s' not found in pipeline '%s'", stepId, pipelineId);
                        return Optional.<PipelineStepConfig>empty();
                    }
                });
    }

    /**
     * Searches all pipelines in a cluster for a step with the given ID.
     */
    public Uni<Optional<PipelineStepConfig>> findPipeStep(String clusterName, String stepId) {
        LOG.debugf("Searching for step '%s' across all pipelines in cluster '%s'", stepId, clusterName);
        
        return listPipelines(clusterName)
                .map(pipelines -> {
                    for (Map.Entry<String, PipelineConfig> entry : pipelines.entrySet()) {
                        String pipelineId = entry.getKey();
                        PipelineConfig pipeline = entry.getValue();
                        
                        if (pipeline.pipelineSteps() != null) {
                            PipelineStepConfig step = pipeline.pipelineSteps().get(stepId);
                            if (step != null) {
                                LOG.debugf("Found step '%s' in pipeline '%s'", stepId, pipelineId);
                                return Optional.of(step);
                            }
                        }
                    }
                    
                    LOG.debugf("Step '%s' not found in any pipeline in cluster '%s'", stepId, clusterName);
                    return Optional.<PipelineStepConfig>empty();
                });
    }

    /**
     * Finds which pipeline contains a step with the given ID.
     */
    public Uni<Optional<String>> getPipelineForStep(String clusterName, String stepId) {
        LOG.debugf("Finding which pipeline contains step '%s' in cluster '%s'", stepId, clusterName);
        
        return listPipelines(clusterName)
                .map(pipelines -> {
                    for (Map.Entry<String, PipelineConfig> entry : pipelines.entrySet()) {
                        String pipelineId = entry.getKey();
                        PipelineConfig pipeline = entry.getValue();
                        
                        if (pipeline.pipelineSteps() != null && pipeline.pipelineSteps().containsKey(stepId)) {
                            LOG.debugf("Step '%s' found in pipeline '%s'", stepId, pipelineId);
                            return Optional.of(pipelineId);
                        }
                    }
                    
                    LOG.debugf("Step '%s' not found in any pipeline in cluster '%s'", stepId, clusterName);
                    return Optional.<String>empty();
                });
    }
}
