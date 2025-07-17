package com.rokkon.pipeline.consul.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokkon.pipeline.config.model.PipelineConfig;
import com.rokkon.pipeline.config.model.PipelineInstance;
import com.rokkon.pipeline.config.model.PipelineInstance.PipelineInstanceStatus;
import com.rokkon.pipeline.config.service.PipelineDefinitionService;
import com.rokkon.pipeline.config.service.PipelineInstanceService;
import com.rokkon.pipeline.config.model.CreateInstanceRequest;
import com.rokkon.pipeline.api.validation.ValidationResult;
import com.rokkon.pipeline.commons.validation.ValidationResultFactory;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.rokkon.pipeline.consul.config.PipelineConsulConfig;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of PipelineInstanceService for managing pipeline instances in Consul.
 */
@ApplicationScoped
public class PipelineInstanceServiceImpl extends ConsulServiceBase implements PipelineInstanceService {

    private static final Logger LOG = Logger.getLogger(PipelineInstanceServiceImpl.class);

    @Inject
    PipelineConsulConfig config;

    @Inject
    PipelineDefinitionService pipelineDefinitionService;

    /**
     * Default constructor for CDI.
     */
    public PipelineInstanceServiceImpl() {
        // Default constructor for CDI
    }

    @Override
    public Uni<List<PipelineInstance>> listInstances(String clusterName) {
        String prefix = config.consul().kvPrefix() + "/pipelines/instances/" + clusterName + "/";
        return consulClient.getKeys(prefix)
            .flatMap(keys -> {
                if (keys == null || keys.isEmpty()) {
                    return Uni.createFrom().item(Collections.emptyList());
                }
                List<Uni<PipelineInstance>> instanceUnis = keys.stream()
                    .map(this::getInstanceFromKey)
                    .collect(Collectors.toList());

                return Uni.combine().all().unis(instanceUnis)
                    .with(list -> list.stream()
                        .filter(Objects::nonNull)
                        .map(obj -> (PipelineInstance) obj)
                        .collect(Collectors.toList()));
            });
    }

    private Uni<PipelineInstance> getInstanceFromKey(String key) {
        return consulClient.getValue(key)
            .map(kv -> {
                if (kv != null && kv.getValue() != null) {
                    try {
                        return objectMapper.readValue(kv.getValue(), PipelineInstance.class);
                    } catch (Exception e) {
                        LOG.warnf("Failed to load pipeline instance from key '%s': %s", key, e.getMessage());
                        return null;
                    }
                }
                return null;
            })
            .onFailure().recoverWithItem(error -> {
                LOG.warnf(error, "Failed to get pipeline instance from key '%s'", key);
                return null;
            });
    }

    @Override
    public Uni<PipelineInstance> getInstance(String clusterName, String instanceId) {
        String key = config.consul().kvPrefix() + "/pipelines/instances/" + clusterName + "/" + instanceId;
        return consulClient.getValue(key)
            .map(keyValue -> {
                if (keyValue != null && keyValue.getValue() != null) {
                    try {
                        String json = keyValue.getValue();
                        return objectMapper.readValue(json, PipelineInstance.class);
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to parse pipeline instance '%s'", instanceId);
                        return null;
                    }
                }
                return null;
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Failed to get pipeline instance '%s'", instanceId);
                return null;
            });
    }

    @Override
    public Uni<ValidationResult> createInstance(String clusterName, CreateInstanceRequest request) {
        return pipelineDefinitionService.getDefinition(request.pipelineDefinitionId())
            .flatMap(definition -> {
                if (definition == null) {
                    return Uni.createFrom().item(ValidationResultFactory.failure(
                        "Pipeline definition '" + request.pipelineDefinitionId() + "' not found"));
                }

                return createInstanceFromDefinition(clusterName, request, definition);
            });
    }

    private Uni<ValidationResult> createInstanceFromDefinition(String clusterName, CreateInstanceRequest request, PipelineConfig definition) {
        // Check if instance already exists
        return instanceExists(clusterName, request.instanceId())
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline instance '" + request.instanceId() + "' already exists in cluster '" + clusterName + "'"));
                }

                // Create the instance
                PipelineInstance instance = new PipelineInstance(
                    request.instanceId(),
                    request.pipelineDefinitionId(),
                    clusterName,
                    request.name() != null ? request.name() : definition.name(),
                    request.description(),
                    PipelineInstanceStatus.STOPPED,
                    request.configOverrides() != null ? request.configOverrides() : Map.of(),
                    request.kafkaTopicPrefix(),
                    request.priority(),
                    request.maxParallelism(),
                    request.metadata() != null ? request.metadata() : Map.of(),
                    Instant.now(),
                    Instant.now(),
                    null,
                    null
                );

                try {
                    // Store in Consul
                    String key = config.consul().kvPrefix() + "/pipelines/instances/" + clusterName + "/" + request.instanceId();
                    String json = objectMapper.writeValueAsString(instance);

                    return createWithCas(key, json)
                        .map(success -> {
                            if (success) {
                                LOG.infof("Created pipeline instance '%s' in cluster '%s' from definition '%s' with CAS", 
                                    request.instanceId(), clusterName, request.pipelineDefinitionId());
                                return ValidationResultFactory.success();
                            } else {
                                return ValidationResultFactory.failure("Failed to store pipeline instance - instance may already exist");
                            }
                        });

                } catch (JsonProcessingException e) {
                    LOG.errorf(e, "Failed to serialize pipeline instance");
                    return Uni.createFrom().item(ValidationResultFactory.failure("Failed to serialize pipeline instance: " + e.getMessage()));
                }
            });
    }

    @Override
    public Uni<ValidationResult> updateInstance(String clusterName, String instanceId, PipelineInstance instance) {
        // Check if exists
        return getInstance(clusterName, instanceId)
            .flatMap(existing -> {
                if (existing == null) {
                    return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline instance '" + instanceId + "' not found in cluster '" + clusterName + "'"));
                }

                // Create updated instance with preserved immutable fields
                PipelineInstance updated = new PipelineInstance(
                    instanceId,
                    existing.pipelineDefinitionId(), // Cannot change definition
                    clusterName,
                    instance.name() != null ? instance.name() : existing.name(),
                    instance.description() != null ? instance.description() : existing.description(),
                    existing.status(), // Status changes through lifecycle methods
                    instance.configOverrides() != null ? instance.configOverrides() : existing.configOverrides(),
                    instance.kafkaTopicPrefix() != null ? instance.kafkaTopicPrefix() : existing.kafkaTopicPrefix(),
                    instance.priority() != null ? instance.priority() : existing.priority(),
                    instance.maxParallelism() != null ? instance.maxParallelism() : existing.maxParallelism(),
                    instance.metadata() != null ? instance.metadata() : existing.metadata(),
                    existing.createdAt(),
                    Instant.now(),
                    existing.startedAt(),
                    existing.stoppedAt()
                );

                try {
                    // Store in Consul
                    String key = config.consul().kvPrefix() + "/pipelines/instances/" + clusterName + "/" + instanceId;
                    String json = objectMapper.writeValueAsString(updated);

                    return updateWithCas(key, PipelineInstance.class, existingInstance -> updated)
                        .map(success -> {
                            if (success) {
                                LOG.infof("Updated pipeline instance '%s' in cluster '%s' with CAS", instanceId, clusterName);
                                return ValidationResultFactory.success();
                            } else {
                                return ValidationResultFactory.failure("Failed to update pipeline instance - too many concurrent updates or instance not found");
                            }
                        });

                } catch (JsonProcessingException e) {
                    LOG.errorf(e, "Failed to serialize pipeline instance");
                    return Uni.createFrom().item(ValidationResultFactory.failure("Failed to serialize pipeline instance: " + e.getMessage()));
                }
            });
    }

    @Override
    public Uni<ValidationResult> deleteInstance(String clusterName, String instanceId) {
        // Check if exists
        return getInstance(clusterName, instanceId)
            .flatMap(instance -> {
                if (instance == null) {
                    return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline instance '" + instanceId + "' not found in cluster '" + clusterName + "'"));
                }

                // Check if running
                if (instance.status() == PipelineInstanceStatus.RUNNING || 
                    instance.status() == PipelineInstanceStatus.STARTING) {
                    return Uni.createFrom().item(ValidationResultFactory.failure("Cannot delete running instance. Stop it first."));
                }

                // Delete from Consul
                String key = config.consul().kvPrefix() + "/pipelines/instances/" + clusterName + "/" + instanceId;

                return consulClient.deleteValue(key)
                    .map(result -> {
                        LOG.infof("Deleted pipeline instance '%s' from cluster '%s'", instanceId, clusterName);
                        return ValidationResultFactory.success();
                    })
                    .onFailure().recoverWithItem(error -> {
                        LOG.errorf(error, "Failed to delete pipeline instance from Consul");
                        return ValidationResultFactory.failure("Failed to delete pipeline instance: " + error.getMessage());
                    });
            });
    }

    @Override
    public Uni<ValidationResult> startInstance(String clusterName, String instanceId) {
        return getInstance(clusterName, instanceId)
            .flatMap(instance -> {
                if (instance == null) {
                    return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline instance '" + instanceId + "' not found in cluster '" + clusterName + "'"));
                }

                if (instance.status() == PipelineInstanceStatus.RUNNING) {
                    return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline instance is already running"));
                }

                // Update status to STARTING then RUNNING
                PipelineInstance updated = new PipelineInstance(
                    instance.instanceId(),
                    instance.pipelineDefinitionId(),
                    instance.clusterName(),
                    instance.name(),
                    instance.description(),
                    PipelineInstanceStatus.RUNNING,
                    instance.configOverrides(),
                    instance.kafkaTopicPrefix(),
                    instance.priority(),
                    instance.maxParallelism(),
                    instance.metadata(),
                    instance.createdAt(),
                    Instant.now(),
                    Instant.now(), // startedAt
                    null // clear stoppedAt
                );

                try {
                    // Store in Consul
                    String key = config.consul().kvPrefix() + "/pipelines/instances/" + clusterName + "/" + instanceId;
                    String json = objectMapper.writeValueAsString(updated);

                    return updateWithCas(key, PipelineInstance.class, existingInstance -> updated)
                        .map(success -> {
                            if (success) {
                                LOG.infof("Started pipeline instance '%s' in cluster '%s' with CAS", instanceId, clusterName);
                                // TODO: Actually start the pipeline processing
                                return ValidationResultFactory.success();
                            } else {
                                return ValidationResultFactory.failure("Failed to update pipeline instance status - too many concurrent updates");
                            }
                        });

                } catch (JsonProcessingException e) {
                    LOG.errorf(e, "Failed to serialize pipeline instance");
                    return Uni.createFrom().item(ValidationResultFactory.failure("Failed to serialize pipeline instance: " + e.getMessage()));
                }
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Failed to start pipeline instance");
                return ValidationResultFactory.failure("Failed to start pipeline instance: " + error.getMessage());
            });
    }

    @Override
    public Uni<ValidationResult> stopInstance(String clusterName, String instanceId) {
        return getInstance(clusterName, instanceId)
            .flatMap(instance -> {
                if (instance == null) {
                    return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline instance '" + instanceId + "' not found in cluster '" + clusterName + "'"));
                }

                if (instance.status() != PipelineInstanceStatus.RUNNING) {
                    return Uni.createFrom().item(ValidationResultFactory.failure("Pipeline instance is not running"));
                }

                // Update status to STOPPING then STOPPED
                PipelineInstance updated = new PipelineInstance(
                    instance.instanceId(),
                    instance.pipelineDefinitionId(),
                    instance.clusterName(),
                    instance.name(),
                    instance.description(),
                    PipelineInstanceStatus.STOPPED,
                    instance.configOverrides(),
                    instance.kafkaTopicPrefix(),
                    instance.priority(),
                    instance.maxParallelism(),
                    instance.metadata(),
                    instance.createdAt(),
                    Instant.now(),
                    instance.startedAt(),
                    Instant.now() // stoppedAt
                );

                try {
                    // Store in Consul
                    String key = config.consul().kvPrefix() + "/pipelines/instances/" + clusterName + "/" + instanceId;
                    String json = objectMapper.writeValueAsString(updated);

                    return updateWithCas(key, PipelineInstance.class, existingInstance -> updated)
                        .map(success -> {
                            if (success) {
                                LOG.infof("Stopped pipeline instance '%s' in cluster '%s' with CAS", instanceId, clusterName);
                                // TODO: Actually stop the pipeline processing
                                return ValidationResultFactory.success();
                            } else {
                                return ValidationResultFactory.failure("Failed to update pipeline instance status - too many concurrent updates");
                            }
                        });

                } catch (JsonProcessingException e) {
                    LOG.errorf(e, "Failed to serialize pipeline instance");
                    return Uni.createFrom().item(ValidationResultFactory.failure("Failed to serialize pipeline instance: " + e.getMessage()));
                }
            })
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Failed to stop pipeline instance");
                return ValidationResultFactory.failure("Failed to stop pipeline instance: " + error.getMessage());
            });
    }

    @Override
    public Uni<Boolean> instanceExists(String clusterName, String instanceId) {
        String key = config.consul().kvPrefix() + "/pipelines/instances/" + clusterName + "/" + instanceId;
        return consulClient.getValue(key)
            .map(keyValue -> keyValue != null && keyValue.getValue() != null)
            .onFailure().recoverWithItem(error -> {
                LOG.errorf(error, "Failed to check if pipeline instance exists");
                return false;
            });
    }

    @Override
    public Uni<List<PipelineInstance>> listInstancesByDefinition(String pipelineDefinitionId) {
        // Note: This lists all instances across all clusters and then filters.
        // This could be inefficient with a very large number of clusters/instances.
        // A more optimized approach might involve a different key structure in Consul if performance becomes an issue.
        return consulClient.getKeys(config.consul().kvPrefix() + "/pipelines/instances/")
            .flatMap(keys -> {
                if (keys == null || keys.isEmpty()) {
                    return Uni.createFrom().item(Collections.<PipelineInstance>emptyList());
                }

                List<Uni<PipelineInstance>> instanceUnis = keys.stream()
                    .map(this::getInstanceFromKey) // Re-use the helper method
                    .collect(Collectors.toList());

                return Uni.combine().all().unis(instanceUnis)
                    .with(list -> list.stream()
                        .filter(Objects::nonNull)
                        .filter(obj -> pipelineDefinitionId.equals(((PipelineInstance)obj).pipelineDefinitionId()))
                        .map(obj -> (PipelineInstance) obj)
                        .collect(Collectors.toList()));
            })
            .onFailure().recoverWithItem((Throwable error) -> {
                LOG.errorf(error, "Failed to list instances by definition '%s'", pipelineDefinitionId);
                return Collections.<PipelineInstance>emptyList();
            });
    }
}
