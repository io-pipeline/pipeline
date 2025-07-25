package io.pipeline.model.validation.validators;

import io.pipeline.api.model.KafkaInputDefinition;
import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.model.PipelineStepConfig;
import io.pipeline.api.model.TransportType;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates Kafka topic naming conventions according to Kafka's requirements:
 * - Topic names can contain letters, numbers, dots, underscores, and hyphens
 * - Topic names cannot exceed 249 characters
 * - Topic names cannot be "." or ".."
 * 
 * Also validates against our design decisions:
 * - Topics should follow pattern: {pipeline-name}.{step-name}.input/output
 * - DLQ topics should follow pattern: {topic}.dlq
 */
@ApplicationScoped
public class KafkaTopicNamingValidator implements PipelineConfigValidator {
    
    private static final String VALID_TOPIC_REGEX = "^[a-zA-Z0-9._-]+$";
    private static final int MAX_TOPIC_LENGTH = 249;
    
    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        PipelineConfig config = (PipelineConfig) validatable;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (config.pipelineSteps() == null) {
            return ValidationResultFactory.success();
        }
        
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step == null) continue;
            
            // Validate Kafka outputs
            if (step.outputs() != null) {
                for (var outputEntry : step.outputs().entrySet()) {
                    String outputName = outputEntry.getKey();
                    var output = outputEntry.getValue();
                    if (output != null && output.transportType() == TransportType.KAFKA &&
                        output.kafkaTransport() != null && output.kafkaTransport().topic() != null) {
                        
                        String topic = output.kafkaTransport().topic();
                        validateTopicName(stepId, outputName, topic, errors, warnings);
                        
                        // Check for DLQ topic pattern
                        String dlqTopic = output.kafkaTransport().getDlqTopic();
                        if (!dlqTopic.equals(topic + ".dlq")) {
                            warnings.add(String.format(
                                "Step '%s' output '%s': DLQ topic '%s' doesn't follow recommended pattern '%s.dlq'",
                                stepId, outputName, dlqTopic, topic
                            ));
                        }
                    }
                }
            }
            
            // Validate Kafka inputs
            if (step.kafkaInputs() != null) {
                for (KafkaInputDefinition input : step.kafkaInputs()) {
                    if (input.listenTopics() != null) {
                        for (String topic : input.listenTopics()) {
                            if (topic != null) {
                                validateTopicName(stepId, "input", topic, errors, warnings);
                            }
                        }
                    }
                }
            }
        }
        
        if (!errors.isEmpty()) {
            return ValidationResultFactory.failure(errors, warnings);
        } else if (!warnings.isEmpty()) {
            return ValidationResultFactory.successWithWarnings(warnings);
        } else {
            return ValidationResultFactory.success();
        }
    }
    
    private void validateTopicName(String stepId, String context, String topicName, 
                                   List<String> errors, List<String> warnings) {
        if (topicName == null || topicName.isBlank()) {
            errors.add(String.format("Step '%s' %s: Topic name cannot be empty", stepId, context));
            return;
        }
        
        // Kafka requirements
        if (!topicName.matches(VALID_TOPIC_REGEX)) {
            errors.add(String.format(
                "Step '%s' %s: Topic name '%s' can only contain letters, numbers, dots, underscores, and hyphens",
                stepId, context, topicName
            ));
        }
        
        if (topicName.length() > MAX_TOPIC_LENGTH) {
            errors.add(String.format(
                "Step '%s' %s: Topic name '%s' cannot exceed %d characters",
                stepId, context, topicName, MAX_TOPIC_LENGTH
            ));
        }
        
        if (topicName.equals(".") || topicName.equals("..")) {
            errors.add(String.format(
                "Step '%s' %s: Topic name cannot be '.' or '..'",
                stepId, context
            ));
        }
        
        // Our naming convention recommendations
        if (!topicName.contains(".")) {
            warnings.add(String.format(
                "Step '%s' %s: Topic name '%s' should follow pattern '{pipeline-name}.{step-name}.{input/output}'",
                stepId, context, topicName
            ));
        }
    }
    
    @Override
    public int getPriority() {
        return 50; // Higher priority for basic naming validation
    }
    
    @Override
    public String getValidatorName() {
        return "KafkaTopicNamingValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Topic naming is important for design and production but can be relaxed for testing
        return Set.of(ValidationMode.PRODUCTION, ValidationMode.DESIGN);
    }
}