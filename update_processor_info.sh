#!/bin/bash

# This script updates all instances of ProcessorInfo creation in the codebase
# to use only one parameter (grpcServiceName) instead of two.
# Rules:
# 1. If the first parameter is non-null and the second is null, keep the first parameter
# 2. If the first parameter is null and the second is non-null, replace the first parameter with the second parameter
# 3. If both parameters are non-null, keep the first parameter (grpcServiceName)

# Function to process a file
process_file() {
    local file=$1
    echo "Processing $file..."
    
    # Case 1: First parameter is a string, second is null
    # Example: new PipelineStepConfig.ProcessorInfo("service", null)
    sed -i 's/new PipelineStepConfig.ProcessorInfo(\s*"\([^"]*\)",\s*null\s*)/new PipelineStepConfig.ProcessorInfo("\1")/g' "$file"
    
    # Case 2: First parameter is null, second is a string
    # Example: new PipelineStepConfig.ProcessorInfo(null, "metadataBean")
    sed -i 's/new PipelineStepConfig.ProcessorInfo(\s*null,\s*"\([^"]*\)"\s*)/new PipelineStepConfig.ProcessorInfo("\1")/g' "$file"
    
    # Case 3: First parameter is empty string, second is a string
    # Example: new PipelineStepConfig.ProcessorInfo("", "internal-bean")
    sed -i 's/new PipelineStepConfig.ProcessorInfo(\s*"",\s*"\([^"]*\)"\s*)/new PipelineStepConfig.ProcessorInfo("\1")/g' "$file"
    
    # Case 4: Both parameters are strings, keep the first one
    # Example: new PipelineStepConfig.ProcessorInfo("grpc-service", "internal-bean")
    sed -i 's/new PipelineStepConfig.ProcessorInfo(\s*"\([^"]*\)",\s*"\([^"]*\)"\s*)/new PipelineStepConfig.ProcessorInfo("\1")/g' "$file"
    
    echo "Completed processing $file"
}

# List of files to process
files=(
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/TransportConfigValidatorTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/pipeline-api/src/test/java/io/pipeline/api/model/PipelineConfigAdvancedTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/ProcessorInfoValidatorTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/StepReferenceValidatorTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/RetryConfigValidatorTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/StepTypeValidatorTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/OutputRoutingValidatorTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/RequiredFieldsValidatorTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/KafkaTopicNamingValidatorTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/pipeline-api/src/test/java/io/pipeline/api/model/PipelineGraphConfigTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/pipeline-api/src/test/java/io/pipeline/api/model/PipelineConfigTestBase.java"
    "/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try/libraries/validators/src/test/java/io/pipeline/validation/validators/SchemaValidatorTest.java"
)

# Process each file
for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        process_file "$file"
    else
        echo "File not found: $file"
    fi
done

echo "All files processed successfully!"