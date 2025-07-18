#!/bin/bash

# Script to fix all import statements in consul-client
# Changes from com.rokkon.pipeline.* to io.pipeline.*

echo "Fixing import statements in consul-client..."

# Define the search and replace patterns
declare -A import_mappings=(
    # Constants
    ["com.rokkon.pipeline.constants.PipelineConstants"]="io.pipeline.api.constants.PipelineConstants"
    
    # Event classes
    ["com.rokkon.pipeline.events.cache.ConsulClusterPipelineChangedEvent"]="io.pipeline.api.events.cache.ConsulClusterPipelineChangedEvent"
    ["com.rokkon.pipeline.events.cache.ConsulModuleRegistrationChangedEvent"]="io.pipeline.api.events.cache.ConsulModuleRegistrationChangedEvent"
    ["com.rokkon.pipeline.events.cache.ConsulPipelineDefinitionChangedEvent"]="io.pipeline.api.events.cache.ConsulPipelineDefinitionChangedEvent"
    ["com.rokkon.pipeline.events.cache.ConsulModuleHealthChanged"]="io.pipeline.api.events.cache.ConsulModuleHealthChanged"
    ["com.rokkon.pipeline.events.ModuleRegistrationResponseEvent"]="io.pipeline.api.events.ModuleRegistrationResponseEvent"
    
    # Validation classes
    ["com.rokkon.pipeline.engine.validation.CompositeValidator"]="io.pipeline.model.validation.CompositeValidator"
    ["com.rokkon.pipeline.api.validation.PipelineConfigValidatable"]="io.pipeline.api.validation.PipelineConfigValidatable"
    ["com.rokkon.pipeline.api.validation.ValidationResult"]="io.pipeline.api.validation.ValidationResult"
    ["com.rokkon.pipeline.api.validation.ValidationMode"]="io.pipeline.api.validation.ValidationMode"
    ["com.rokkon.pipeline.commons.validation.ValidationResultFactory"]="io.pipeline.common.validation.ValidationResultFactory"
    
    # Config and service classes
    ["com.rokkon.pipeline.config.model"]="io.pipeline.api.config.model"
    ["com.rokkon.pipeline.config.service"]="io.pipeline.api.config.service"
    ["com.rokkon.pipeline.commons.model"]="io.pipeline.common.model"
    
    # Utility classes
    ["com.rokkon.pipeline.util.ObjectMapperFactory"]="io.pipeline.common.util.ObjectMapperFactory"
    ["com.rokkon.pipeline.testing.util.UnifiedTestProfile"]="io.pipeline.common.testing.UnifiedTestProfile"
    
    # Config model specific classes
    ["com.rokkon.pipeline.config.model.PipelineInstance"]="io.pipeline.api.config.model.PipelineInstance"
    ["com.rokkon.pipeline.config.model.CreateInstanceRequest"]="io.pipeline.api.config.model.CreateInstanceRequest"
    ["com.rokkon.pipeline.config.model.PipelineConfig"]="io.pipeline.api.config.model.PipelineConfig"
    ["com.rokkon.pipeline.config.model.Cluster"]="io.pipeline.api.config.model.Cluster"
    ["com.rokkon.pipeline.config.model.ClusterMetadata"]="io.pipeline.api.config.model.ClusterMetadata"
    ["com.rokkon.pipeline.config.model.PipelineStepConfig"]="io.pipeline.api.config.model.PipelineStepConfig"
    ["com.rokkon.pipeline.config.model.StepType"]="io.pipeline.api.config.model.StepType"
    ["com.rokkon.pipeline.config.model.ModuleWhitelistRequest"]="io.pipeline.api.config.model.ModuleWhitelistRequest"
    ["com.rokkon.pipeline.config.model.ModuleWhitelistResponse"]="io.pipeline.api.config.model.ModuleWhitelistResponse"
    
    # Service classes
    ["com.rokkon.pipeline.config.service.PipelineInstanceService"]="io.pipeline.api.config.service.PipelineInstanceService"
    ["com.rokkon.pipeline.config.service.ClusterService"]="io.pipeline.api.config.service.ClusterService"
    ["com.rokkon.pipeline.config.service.ModuleWhitelistService"]="io.pipeline.api.config.service.ModuleWhitelistService"
    ["com.rokkon.pipeline.config.service.PipelineDefinitionService"]="io.pipeline.api.config.service.PipelineDefinitionService"
    ["com.rokkon.pipeline.config.service.PipelineConfigService"]="io.pipeline.api.config.service.PipelineConfigService"
    
    # Commons model classes
    ["com.rokkon.pipeline.commons.model.GlobalModuleRegistryService"]="io.pipeline.common.model.GlobalModuleRegistryService"
    ["com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ModuleRegistration"]="io.pipeline.common.model.GlobalModuleRegistryService.ModuleRegistration"
    ["com.rokkon.pipeline.commons.model.GlobalModuleRegistryService.ServiceHealthStatus"]="io.pipeline.common.model.GlobalModuleRegistryService.ServiceHealthStatus"
)

# Function to replace imports in a file
fix_file_imports() {
    local file="$1"
    local temp_file="${file}.tmp"
    
    echo "Processing $file..."
    
    # Create a copy of the file
    cp "$file" "$temp_file"
    
    # Apply all the replacements
    for old_import in "${!import_mappings[@]}"; do
        new_import="${import_mappings[$old_import]}"
        sed -i "s|import ${old_import}|import ${new_import}|g" "$temp_file"
    done
    
    # Replace the original file
    mv "$temp_file" "$file"
}

# Find all Java files and fix their imports
find src -name "*.java" -type f | while read -r file; do
    if grep -q "import com\.rokkon\.pipeline\." "$file"; then
        fix_file_imports "$file"
    fi
done

echo "Import fixing completed!"