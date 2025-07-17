package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.Cluster;
import com.rokkon.pipeline.config.model.ClusterMetadata;
import com.rokkon.pipeline.config.model.PipelineClusterConfig;
import com.rokkon.pipeline.config.model.PipelineGraphConfig;
import com.rokkon.pipeline.config.model.PipelineModuleMap;
import com.rokkon.pipeline.config.service.ClusterService;
import com.rokkon.pipeline.api.validation.ValidationResult;
import com.rokkon.pipeline.commons.validation.ValidationResultFactory;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of the ClusterService interface that manages cluster configurations in Consul.
 * This service provides functionality for creating, retrieving, listing, and deleting clusters,
 * as well as managing their associated metadata and configurations.
 * 
 * All write operations use CAS (Compare-And-Swap) to ensure safe concurrent updates.
 */
@ApplicationScoped
public class ClusterServiceImpl extends ConsulServiceBase implements ClusterService {
    private static final Logger LOG = Logger.getLogger(ClusterServiceImpl.class);

    /**
     * Default constructor for CDI.
     * Dependencies are injected by the CDI container.
     */
    public ClusterServiceImpl() {
        // Default constructor for CDI
    }


    public Uni<ValidationResult> createCluster(String clusterName) {
        LOG.infof("Creating cluster: %s", clusterName);

        // Validate cluster name
        if (clusterName == null || clusterName.trim().isEmpty()) {
            return Uni.createFrom().item(
                ValidationResultFactory.failure("Cluster name cannot be empty")
            );
        }

        // Check if cluster already exists
        return clusterExists(clusterName)
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().item(
                        ValidationResultFactory.failure("Cluster '" + clusterName + "' already exists")
                    );
                }

                // Create cluster metadata
                ClusterMetadata metadata = new ClusterMetadata(
                    clusterName,
                    Instant.now(),
                    null,
                    Map.of("status", "active", "version", "1.0")
                );

                // Store metadata first
                return storeClusterMetadata(clusterName, metadata)
                    .flatMap(result -> {
                        if (!result.valid()) {
                            return Uni.createFrom().item(result);
                        }

                        // Create initial PipelineClusterConfig
                        return createInitialClusterConfig(clusterName);
                    });
            });
    }

    public Uni<Optional<ClusterMetadata>> getCluster(String clusterName) {
        String key = buildClusterKey(clusterName) + "/metadata";

        return consulClient.getValue(key)
            .map(keyValue -> {
                if (keyValue == null || keyValue.getValue() == null) {
                    return Optional.<ClusterMetadata>empty();
                }

                try {
                    String json = keyValue.getValue();
                    ClusterMetadata metadata = objectMapper.readValue(json, ClusterMetadata.class);
                    return Optional.of(metadata);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to parse cluster metadata for %s", clusterName);
                    return Optional.<ClusterMetadata>empty();
                }
            })
            .onFailure().recoverWithItem(error -> {
                LOG.debugf("Failed to get cluster metadata for %s: %s", clusterName, error.getMessage());
                return Optional.empty();
            });
    }

    public Uni<Boolean> clusterExists(String clusterName) {
        return getCluster(clusterName).map(Optional::isPresent);
    }

    public Uni<ValidationResult> deleteCluster(String clusterName) {
        String key = buildClusterKey(clusterName);

        return consulClient.deleteValues(key)
            .map(response -> {
                LOG.infof("Deleted cluster: %s", clusterName);
                return ValidationResultFactory.success();
            })
            .onFailure().recoverWithItem((Throwable error) -> {
                LOG.errorf(error, "Failed to delete cluster: %s", clusterName);
                return ValidationResultFactory.failure("Failed to delete cluster: " + error.getMessage());
            });
    }

    public Uni<List<Cluster>> listClusters() {
        String prefix = kvPrefix + "/clusters";
        LOG.debugf("Listing clusters with prefix: %s", prefix);

        return consulClient.getKeys(prefix)
            .onItem().transformToUni(keys -> {
                LOG.debugf("Found %d keys under prefix %s", keys != null ? keys.size() : 0, prefix);
                if (keys != null && !keys.isEmpty()) {
                    LOG.debugf("Keys: %s", keys);
                }

                if (keys == null || keys.isEmpty()) {
                    return Uni.createFrom().item(new ArrayList<Cluster>());
                }

                // Extract unique cluster names
                Set<String> clusterNames = new java.util.HashSet<>();
                for (String key : keys) {
                    // Remove the prefix to get the relative path
                    if (key.startsWith(prefix + "/")) {
                        String relativePath = key.substring(prefix.length() + 1);
                        String[] parts = relativePath.split("/");
                        // First part after prefix should be the cluster name
                        if (parts.length > 0 && !parts[0].isEmpty()) {
                            clusterNames.add(parts[0]);
                        }
                    }
                }
                LOG.debugf("Extracted cluster names: %s", clusterNames);

                // Get metadata for each cluster
                List<Uni<Optional<Cluster>>> clusterUnis = new ArrayList<>();
                for (String name : clusterNames) {
                    clusterUnis.add(
                        getCluster(name).map(metaOpt -> {
                            if (metaOpt.isPresent()) {
                                ClusterMetadata meta = metaOpt.get();
                                Map<String, String> metadataStrings = new java.util.HashMap<>();
                                meta.metadata().forEach((k, v) -> metadataStrings.put(k, String.valueOf(v)));

                                // Handle null createdAt gracefully
                                String createdAtStr = meta.createdAt() != null ? 
                                    meta.createdAt().toString() : 
                                    Instant.now().toString();

                                return Optional.of(new Cluster(
                                    name,
                                    createdAtStr,
                                    meta
                                ));
                            }
                            return Optional.<Cluster>empty();
                        })
                    );
                }

                return Uni.combine().all().unis(clusterUnis)
                    .with(list -> {
                        List<Cluster> result = new ArrayList<>();
                        for (Object obj : list) {
                            @SuppressWarnings("unchecked")
                            Optional<Cluster> opt = (Optional<Cluster>) obj;
                            opt.ifPresent(result::add);
                        }
                        return result;
                    });
            })
            .onFailure().recoverWithItem((Throwable error) -> {
                LOG.errorf(error, "Failed to list clusters");
                return new ArrayList<>();
            });
    }

    private Uni<ValidationResult> storeClusterMetadata(String clusterName, ClusterMetadata metadata) {
        try {
            String json = objectMapper.writeValueAsString(metadata);
            String key = buildClusterKey(clusterName) + "/metadata";
            LOG.debugf("Storing cluster metadata at key: %s", key);

            // Use CAS with index 0 to ensure we only create if it doesn't exist
            return createWithCas(key, json)
                .map(success -> {
                    if (success) {
                        LOG.infof("Successfully created cluster with CAS: %s", clusterName);
                        return ValidationResultFactory.success();
                    } else {
                        LOG.warnf("Failed to create cluster - key may already exist: %s", key);
                        return ValidationResultFactory.failure("Cluster already exists or CAS operation failed");
                    }
                })
                .onFailure().recoverWithItem((Throwable error) -> {
                    LOG.errorf(error, "Failed to store cluster metadata");
                    return ValidationResultFactory.failure("Failed to store cluster metadata: " + error.getMessage());
                });
        } catch (Exception e) {
            LOG.error("Failed to serialize cluster metadata", e);
            return Uni.createFrom().item(ValidationResultFactory.failure("Failed to serialize cluster metadata: " + e.getMessage()));
        }
    }

    private String buildClusterKey(String clusterName) {
        return kvPrefix + "/clusters/" + clusterName;
    }

    private Uni<ValidationResult> createInitialClusterConfig(String clusterName) {
        try {
            // Create initial empty PipelineClusterConfig
            PipelineGraphConfig emptyGraph = new PipelineGraphConfig(Map.of());
            PipelineModuleMap emptyModuleMap = new PipelineModuleMap(Map.of());

            PipelineClusterConfig initialConfig = new PipelineClusterConfig(
                clusterName,
                emptyGraph,
                emptyModuleMap,
                null,  // no default pipeline
                Set.of(),  // no allowed Kafka topics yet
                Set.of()   // no allowed gRPC services yet
            );

            String json = objectMapper.writeValueAsString(initialConfig);
            String key = buildClusterKey(clusterName) + "/config";

            // Use CAS with index 0 to ensure we only create if it doesn't exist
            return createWithCas(key, json)
                .map(success -> {
                    if (success) {
                        LOG.infof("Created initial config for cluster with CAS: %s", clusterName);
                        return ValidationResultFactory.success();
                    } else {
                        LOG.warnf("Failed to create initial cluster config - key may already exist: %s", key);
                        return ValidationResultFactory.failure("Cluster config already exists or CAS operation failed");
                    }
                })
                .onFailure().recoverWithItem((Throwable error) -> {
                    LOG.errorf(error, "Failed to create initial cluster config");
                    return ValidationResultFactory.failure("Failed to create cluster config: " + error.getMessage());
                });
        } catch (Exception e) {
            LOG.error("Failed to serialize initial cluster config", e);
            return Uni.createFrom().item(ValidationResultFactory.failure("Failed to create cluster config: " + e.getMessage()));
        }
    }
    
    /**
     * Updates an existing cluster configuration using CAS to prevent concurrent update conflicts.
     * 
     * @param clusterName The name of the cluster to update
     * @param updateFunction Function that takes the current PipelineClusterConfig and returns the updated version
     * @return Uni<ValidationResult> indicating success or failure
     */
    public Uni<ValidationResult> updateClusterConfig(String clusterName, 
                                                   java.util.function.Function<PipelineClusterConfig, PipelineClusterConfig> updateFunction) {
        String key = buildClusterKey(clusterName) + "/config";
        
        return updateWithCas(key, PipelineClusterConfig.class, updateFunction)
            .map(success -> {
                if (success) {
                    LOG.infof("Successfully updated cluster config with CAS: %s", clusterName);
                    return ValidationResultFactory.success();
                } else {
                    return ValidationResultFactory.failure(
                        "Failed to update cluster config - too many concurrent updates or cluster not found");
                }
            })
            .onFailure().recoverWithItem((Throwable error) -> {
                LOG.errorf(error, "Failed to update cluster config: %s", clusterName);
                return ValidationResultFactory.failure("Failed to update cluster config: " + error.getMessage());
            });
    }
    
    /**
     * Updates cluster metadata using CAS to prevent concurrent update conflicts.
     * 
     * @param clusterName The name of the cluster to update
     * @param updateFunction Function that takes the current ClusterMetadata and returns the updated version
     * @return Uni<ValidationResult> indicating success or failure
     */
    public Uni<ValidationResult> updateClusterMetadata(String clusterName,
                                                     java.util.function.Function<ClusterMetadata, ClusterMetadata> updateFunction) {
        String key = buildClusterKey(clusterName) + "/metadata";
        
        return updateWithCas(key, ClusterMetadata.class, updateFunction)
            .map(success -> {
                if (success) {
                    LOG.infof("Successfully updated cluster metadata with CAS: %s", clusterName);
                    return ValidationResultFactory.success();
                } else {
                    return ValidationResultFactory.failure(
                        "Failed to update cluster metadata - too many concurrent updates or cluster not found");
                }
            })
            .onFailure().recoverWithItem((Throwable error) -> {
                LOG.errorf(error, "Failed to update cluster metadata: %s", clusterName);
                return ValidationResultFactory.failure("Failed to update cluster metadata: " + error.getMessage());
            });
    }
}
