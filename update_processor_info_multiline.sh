#!/bin/bash

# This script updates all instances of ProcessorInfo creation in the codebase
# to use only one parameter (grpcServiceName) instead of two.
# This version handles multi-line patterns.

# Function to process a file
process_file() {
    local file=$1
    echo "Processing $file..."
    
    # Use perl to handle multi-line patterns
    # This replaces patterns like:
    # new PipelineStepConfig.ProcessorInfo(
    #     "service", null
    # )
    # with:
    # new PipelineStepConfig.ProcessorInfo(
    #     "service"
    # )
    perl -0777 -i -pe 's/new PipelineStepConfig\.ProcessorInfo\(\s*"([^"]*)"\s*,\s*null\s*\)/new PipelineStepConfig.ProcessorInfo(\n            "$1"\n        )/g' "$file"
    
    # Handle case where first parameter is null and second is non-null
    # new PipelineStepConfig.ProcessorInfo(
    #     null, "metadataBean"
    # )
    # with:
    # new PipelineStepConfig.ProcessorInfo(
    #     "metadataBean"
    # )
    perl -0777 -i -pe 's/new PipelineStepConfig\.ProcessorInfo\(\s*null\s*,\s*"([^"]*)"\s*\)/new PipelineStepConfig.ProcessorInfo(\n            "$1"\n        )/g' "$file"
    
    # Handle case where first parameter is empty string and second is non-null
    # new PipelineStepConfig.ProcessorInfo(
    #     "", "internal-bean"
    # )
    # with:
    # new PipelineStepConfig.ProcessorInfo(
    #     "internal-bean"
    # )
    perl -0777 -i -pe 's/new PipelineStepConfig\.ProcessorInfo\(\s*""\s*,\s*"([^"]*)"\s*\)/new PipelineStepConfig.ProcessorInfo(\n            "$1"\n        )/g' "$file"
    
    # Handle case where both parameters are non-null
    # new PipelineStepConfig.ProcessorInfo(
    #     "grpc-service", "internal-bean"
    # )
    # with:
    # new PipelineStepConfig.ProcessorInfo(
    #     "grpc-service"
    # )
    perl -0777 -i -pe 's/new PipelineStepConfig\.ProcessorInfo\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)/new PipelineStepConfig.ProcessorInfo(\n            "$1"\n        )/g' "$file"
    
    echo "Completed processing $file"
}

# Find all Java files that contain "ProcessorInfo"
files=$(grep -l "ProcessorInfo" --include="*.java" -r .)

# Process each file
for file in $files; do
    if [ -f "$file" ]; then
        process_file "$file"
    else
        echo "File not found: $file"
    fi
done

echo "All files processed successfully!"