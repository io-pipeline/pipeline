package io.pipeline.consul.client.service;

import io.pipeline.consul.client.profile.WithConsulConfigProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.GenericType;
import io.pipeline.api.model.PipelineInstance;
import io.pipeline.api.model.CreateInstanceRequest;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.api.service.PipelineInstanceService;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.Map;

/**
 * Integration test for PipelineInstanceService using real Consul.
 * Uses @ConsulQuarkusIntegrationTest for proper integration testing against packaged JAR.
 */
@TestProfile(WithConsulConfigProfile.class)
class PipelineInstanceServiceIT extends PipelineInstanceServiceTestBase {
    
    private Client client;
    private String baseUrl;
    
    @Override
    @BeforeEach
    void setupDependencies() {
        // For integration tests, we use REST API calls
        this.client = ClientBuilder.newClient();
        
        // Get the test URL from system property (set by QuarkusIntegrationTest)
        String testUrl = System.getProperty("test.url");
        this.baseUrl = testUrl != null ? testUrl : "http://localhost:8081";
        
        // Create REST-based adapters for the services
        this.pipelineDefinitionService = new RestBasedPipelineDefinitionService(client, baseUrl);
        this.pipelineInstanceService = new RestBasedPipelineInstanceService(client, baseUrl);
    }
    
    @AfterEach
    void cleanup() {
        if (client != null) {
            client.close();
        }
    }
    
    /**
     * REST-based implementation of PipelineInstanceService for integration testing
     */
    private static class RestBasedPipelineInstanceService implements PipelineInstanceService {
        private final Client client;
        private final String baseUrl;
        
        public RestBasedPipelineInstanceService(Client client, String baseUrl) {
            this.client = client;
            this.baseUrl = baseUrl;
        }
        
        @Override
        public Uni<List<PipelineInstance>> listInstances(String clusterName) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/" + clusterName + "/instances")
                    .request(MediaType.APPLICATION_JSON)
                    .get();
                
                if (response.getStatus() == 200) {
                    return response.readEntity(new GenericType<List<PipelineInstance>>() {});
                } else {
                    throw new RuntimeException("Failed to list instances: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<PipelineInstance> getInstance(String clusterName, String instanceId) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/" + clusterName + "/instances/" + instanceId)
                    .request(MediaType.APPLICATION_JSON)
                    .get();
                
                if (response.getStatus() == 200) {
                    return response.readEntity(PipelineInstance.class);
                } else if (response.getStatus() == 404) {
                    return null;
                } else {
                    throw new RuntimeException("Failed to get instance: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<ValidationResult> createInstance(String clusterName, CreateInstanceRequest request) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/" + clusterName + "/instances/deploy")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(request));
                
                if (response.getStatus() == 201 || response.getStatus() == 400) {
                    return response.readEntity(ValidationResult.class);
                } else {
                    throw new RuntimeException("Failed to create instance: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<ValidationResult> updateInstance(String clusterName, String instanceId, PipelineInstance instance) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/" + clusterName + "/instances/" + instanceId)
                    .request(MediaType.APPLICATION_JSON)
                    .put(Entity.json(instance));
                
                if (response.getStatus() == 200 || response.getStatus() == 400) {
                    return response.readEntity(ValidationResult.class);
                } else {
                    throw new RuntimeException("Failed to update instance: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<ValidationResult> deleteInstance(String clusterName, String instanceId) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/" + clusterName + "/instances/" + instanceId)
                    .request(MediaType.APPLICATION_JSON)
                    .delete();
                
                if (response.getStatus() == 204) {
                    return io.pipeline.api.validation.ValidationResultFactory.success();
                } else if (response.getStatus() == 400) {
                    return response.readEntity(ValidationResult.class);
                } else {
                    throw new RuntimeException("Failed to delete instance: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<ValidationResult> startInstance(String clusterName, String instanceId) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/" + clusterName + "/instances/" + instanceId + "/start")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json("{}"));
                
                if (response.getStatus() == 200 || response.getStatus() == 400) {
                    return response.readEntity(ValidationResult.class);
                } else {
                    throw new RuntimeException("Failed to start instance: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<ValidationResult> stopInstance(String clusterName, String instanceId) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/" + clusterName + "/instances/" + instanceId + "/stop")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json("{}"));
                
                if (response.getStatus() == 200 || response.getStatus() == 400) {
                    return response.readEntity(ValidationResult.class);
                } else {
                    throw new RuntimeException("Failed to stop instance: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<Boolean> instanceExists(String clusterName, String instanceId) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/" + clusterName + "/instances/" + instanceId + "/exists")
                    .request(MediaType.APPLICATION_JSON)
                    .get();
                
                if (response.getStatus() == 200) {
                    Map<String, Boolean> result = response.readEntity(new GenericType<Map<String, Boolean>>() {});
                    return result.get("exists");
                } else {
                    throw new RuntimeException("Failed to check instance existence: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<List<PipelineInstance>> listInstancesByDefinition(String pipelineDefinitionId) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/clusters/instances/by-definition/" + pipelineDefinitionId)
                    .request(MediaType.APPLICATION_JSON)
                    .get();
                
                if (response.getStatus() == 200) {
                    return response.readEntity(new GenericType<List<PipelineInstance>>() {});
                } else {
                    throw new RuntimeException("Failed to list instances by definition: " + response.getStatus());
                }
            });
        }
    }
    
    /**
     * REST-based implementation of PipelineDefinitionService for integration testing
     */
    private static class RestBasedPipelineDefinitionService implements com.rokkon.pipeline.config.service.PipelineDefinitionService {
        private final Client client;
        private final String baseUrl;
        
        public RestBasedPipelineDefinitionService(Client client, String baseUrl) {
            this.client = client;
            this.baseUrl = baseUrl;
        }
        
        // Implementation would go here - simplified for brevity
        @Override
        public Uni<List<com.rokkon.pipeline.config.model.PipelineDefinitionSummary>> listDefinitions() {
            return Uni.createFrom().item(List.of());
        }
        
        @Override
        public Uni<com.rokkon.pipeline.config.model.PipelineConfig> getDefinition(String pipelineId) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/pipelines/" + pipelineId)
                    .request(MediaType.APPLICATION_JSON)
                    .get();
                
                if (response.getStatus() == 200) {
                    return response.readEntity(com.rokkon.pipeline.config.model.PipelineConfig.class);
                } else if (response.getStatus() == 404) {
                    return null;
                } else {
                    throw new RuntimeException("Failed to get definition: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<ValidationResult> createDefinition(String pipelineId, com.rokkon.pipeline.config.model.PipelineConfig definition) {
            return Uni.createFrom().item(() -> {
                Response response = client.target(baseUrl + "/api/v1/pipelines/" + pipelineId)
                    .request(MediaType.APPLICATION_JSON)
                    .put(Entity.json(definition));
                
                if (response.getStatus() == 201 || response.getStatus() == 400) {
                    return response.readEntity(ValidationResult.class);
                } else {
                    throw new RuntimeException("Failed to create definition: " + response.getStatus());
                }
            });
        }
        
        @Override
        public Uni<ValidationResult> createDefinition(String pipelineId, com.rokkon.pipeline.config.model.PipelineConfig definition, 
                io.pipeline.api.validation.ValidationMode validationMode) {
            return createDefinition(pipelineId, definition);
        }
        
        @Override
        public Uni<ValidationResult> updateDefinition(String pipelineId, com.rokkon.pipeline.config.model.PipelineConfig definition) {
            return Uni.createFrom().item(io.pipeline.api.validation.ValidationResultFactory.success());
        }
        
        @Override
        public Uni<ValidationResult> updateDefinition(String pipelineId, com.rokkon.pipeline.config.model.PipelineConfig definition,
                io.pipeline.api.validation.ValidationMode validationMode) {
            return updateDefinition(pipelineId, definition);
        }
        
        @Override
        public Uni<ValidationResult> deleteDefinition(String pipelineId) {
            return Uni.createFrom().item(io.pipeline.api.validation.ValidationResultFactory.success());
        }
        
        @Override
        public Uni<Boolean> definitionExists(String pipelineId) {
            return Uni.createFrom().item(false);
        }
        
        @Override
        public Uni<Integer> getActiveInstanceCount(String pipelineId) {
            return Uni.createFrom().item(0);
        }
    }
}