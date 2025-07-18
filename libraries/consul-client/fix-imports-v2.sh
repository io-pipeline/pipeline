#!/bin/bash

# Comprehensive import fixing script for consul-client
# Based on actual available classes in the new framework

echo "Fixing imports with correct package mappings..."

# Function to fix imports in a file
fix_file_imports() {
    local file="$1"
    local temp_file="${file}.tmp"
    
    echo "Processing $file..."
    
    # Create a copy of the file
    cp "$file" "$temp_file"
    
    # Fix all the imports to use the correct package structure
    # Model classes
    sed -i 's|import io\.pipeline\.api\.config\.model\.|import io.pipeline.api.model.|g' "$temp_file"
    sed -i 's|import com\.rokkon\.pipeline\.config\.model\.|import io.pipeline.api.model.|g' "$temp_file"
    
    # Service interfaces
    sed -i 's|import io\.pipeline\.api\.config\.service\.|import io.pipeline.api.service.|g' "$temp_file"
    sed -i 's|import com\.rokkon\.pipeline\.config\.service\.|import io.pipeline.api.service.|g' "$temp_file"
    
    # Common/Commons model classes
    sed -i 's|import io\.pipeline\.common\.model\.GlobalModuleRegistryService|import io.pipeline.api.service.ModuleRegistryService|g' "$temp_file"
    sed -i 's|import com\.rokkon\.pipeline\.commons\.model\.GlobalModuleRegistryService|import io.pipeline.api.service.ModuleRegistryService|g' "$temp_file"
    
    # Static imports for inner classes
    sed -i 's|import static com\.rokkon\.pipeline\.commons\.model\.GlobalModuleRegistryService\.ModuleStatus|import static io.pipeline.api.service.ModuleRegistryService.ModuleStatus|g' "$temp_file"
    sed -i 's|import static io\.pipeline\.common\.model\.GlobalModuleRegistryService\.ModuleStatus|import static io.pipeline.api.service.ModuleRegistryService.ModuleStatus|g' "$temp_file"
    
    # Fix class references - change GlobalModuleRegistryService to ModuleRegistryService
    sed -i 's|GlobalModuleRegistryService|ModuleRegistryService|g' "$temp_file"
    
    # Fix inner class references
    sed -i 's|ModuleRegistryService\.ModuleRegistration|ModuleRegistryService.ModuleRegistration|g' "$temp_file"
    sed -i 's|ModuleRegistryService\.ServiceHealthStatus|ModuleRegistryService.ServiceHealthStatus|g' "$temp_file"
    
    # Fix validation classes
    sed -i 's|import io\.pipeline\.common\.validation\.|import io.pipeline.api.validation.|g' "$temp_file"
    
    # Fix testing classes
    sed -i 's|import io\.pipeline\.common\.testing\.|import io.pipeline.api.testing.|g' "$temp_file"
    
    # Fix any remaining com.rokkon.pipeline references
    sed -i 's|com\.rokkon\.pipeline\.commons\.validation|io.pipeline.api.validation|g' "$temp_file"
    sed -i 's|com\.rokkon\.pipeline\.api\.validation|io.pipeline.api.validation|g' "$temp_file"
    sed -i 's|com\.rokkon\.pipeline\.testing\.util|io.pipeline.api.testing|g' "$temp_file"
    sed -i 's|com\.rokkon\.pipeline\.util|io.pipeline.api.util|g' "$temp_file"
    
    # Fix util classes
    sed -i 's|import io\.pipeline\.common\.util\.|import io.pipeline.api.util.|g' "$temp_file"
    
    # Replace the original file
    mv "$temp_file" "$file"
}

# Fix all Java files
find src -name "*.java" -type f | while read -r file; do
    fix_file_imports "$file"
done

echo "Import fixing v2 completed!"