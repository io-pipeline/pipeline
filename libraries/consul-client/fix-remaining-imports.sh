#!/bin/bash

# Fix remaining import issues in consul-client

echo "Fixing remaining import issues..."

# Fix ValidationResultFactory imports
find src -name "*.java" -type f -exec sed -i 's|import io\.pipeline\.api\.validation\.ValidationResultFactory|import io.pipeline.common.validation.ValidationResultFactory|g' {} \;

# Fix EmptyValidationResult imports
find src -name "*.java" -type f -exec sed -i 's|import io\.pipeline\.api\.validation\.EmptyValidationResult|import io.pipeline.common.validation.EmptyValidationResult|g' {} \;

# Add missing validator imports to ValidatorConfiguration.java
VALIDATOR_CONFIG="src/main/java/com/rokkon/pipeline/consul/config/ValidatorConfiguration.java"
if [ -f "$VALIDATOR_CONFIG" ]; then
    echo "Adding validator imports to ValidatorConfiguration.java..."
    
    # Add all validator imports after the existing imports
    sed -i '/import io.pipeline.model.validation.CompositeValidator;/a\
import io.pipeline.model.validation.validators.KafkaTopicNamingValidator;\
import io.pipeline.model.validation.validators.OutputRoutingValidator;\
import io.pipeline.model.validation.validators.RetryConfigValidator;\
import io.pipeline.model.validation.validators.TransportConfigValidator;\
import io.pipeline.model.validation.validators.ProcessorInfoValidator;\
import io.pipeline.model.validation.validators.RequiredFieldsValidator;\
import io.pipeline.model.validation.validators.NamingConventionValidator;\
import io.pipeline.model.validation.validators.StepTypeValidator;\
import io.pipeline.model.validation.validators.IntraPipelineLoopValidator;\
import io.pipeline.model.validation.validators.StepReferenceValidator;' "$VALIDATOR_CONFIG"
fi

echo "Remaining import fixes completed!"