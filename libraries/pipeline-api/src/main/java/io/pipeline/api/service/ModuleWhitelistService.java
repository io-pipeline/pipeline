package io.pipeline.api.service;

import io.pipeline.api.model.ModuleWhitelistRequest;
import io.pipeline.api.model.ModuleWhitelistResponse;
import io.pipeline.api.model.PipelineModuleConfiguration;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Service interface for managing module whitelisting in clusters.
 * Handles adding modules to the PipelineModuleMap after verifying they exist in Consul.
 */
public interface ModuleWhitelistService {
    
    /**
     * Whitelist a module for a specific cluster.
     * This adds the module to the cluster's PipelineModuleMap if it exists in Consul.
     * 
     * @param clusterName The cluster to add the module to
     * @param request The module whitelist request
     * @return Response indicating success or failure
     */
    Uni<ModuleWhitelistResponse> whitelistModule(String clusterName, ModuleWhitelistRequest request);
    
    /**
     * Remove a module from the whitelist for a specific cluster.
     * 
     * @param clusterName The cluster to remove the module from
     * @param grpcServiceName The name of the gRPC service to remove
     * @return Response indicating success or failure
     */
    Uni<ModuleWhitelistResponse> removeModuleFromWhitelist(String clusterName, String grpcServiceName);
    
    /**
     * List all whitelisted modules for a specific cluster.
     * 
     * @param clusterName The cluster to list modules for
     * @return List of whitelisted module configurations
     */
    Uni<List<PipelineModuleConfiguration>> listWhitelistedModules(String clusterName);
}