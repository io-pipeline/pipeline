package io.pipeline.model.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates transport configurations (Kafka and gRPC) for consistency and best practices.
 */
@ApplicationScoped
public class TransportConfigValidator implements PipelineConfigValidator {
    
    private static final Logger LOG = Logger.getLogger(TransportConfigValidator.class);
    
    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        LOG.debugf("TransportConfigValidator.validate called with: %s", validatable);
        PipelineConfig config = (PipelineConfig) validatable;
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return ValidationResultFactory.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        for (var entry : config.pipelineSteps().entrySet()) {
            String stepId = entry.getKey();
            PipelineStepConfig step = entry.getValue();
            
            if (step == null) {
                continue;
            }
            
            String stepPrefix = "Step '" + stepId + "': ";
            
            // Validate Kafka inputs
            if (step.kafkaInputs() != null) {
                for (int i = 0; i < step.kafkaInputs().size(); i++) {
                    KafkaInputDefinition kafkaInput = step.kafkaInputs().get(i);
                    if (kafkaInput != null) {
                        validateKafkaInput(stepPrefix + "Kafka input[" + i + "]: ", kafkaInput, errors, warnings);
                    }
                }
            }
            
            // Validate outputs
            if (step.outputs() != null) {
                for (var outputEntry : step.outputs().entrySet()) {
                    String outputKey = outputEntry.getKey();
                    PipelineStepConfig.OutputTarget output = outputEntry.getValue();
                    
                    if (output != null) {
                        String outputPrefix = stepPrefix + "Output '" + outputKey + "': ";
                        
                        // Validate based on transport type
                        LOG.debugf("Checking output transport type: %s, kafkaTransport: %s, grpcTransport: %s", 
                            output.transportType(), output.kafkaTransport(), output.grpcTransport());
                        if (output.transportType() == TransportType.KAFKA && output.kafkaTransport() != null) {
                            LOG.debugf("Validating Kafka transport config: %s", output.kafkaTransport());
                            validateKafkaTransport(outputPrefix, output.kafkaTransport(), errors, warnings);
                        } else if (output.transportType() == TransportType.GRPC && output.grpcTransport() != null) {
                            validateGrpcTransport(outputPrefix, output.grpcTransport(), errors, warnings);
                        }
                        
                        // Note: Transport type consistency is enforced by the model, no need to check here
                    }
                }
            }
        }
        
        ValidationResult result = errors.isEmpty() ? 
            (warnings.isEmpty() ? ValidationResultFactory.success() : ValidationResultFactory.successWithWarnings(warnings)) : 
            ValidationResultFactory.failure(errors, warnings);
        LOG.debugf("TransportConfigValidator returning result: valid=%s, errors=%s, warnings=%s", 
            result.valid(), result.errors(), result.warnings());
        return result;
    }
    
    private void validateKafkaInput(String prefix, KafkaInputDefinition kafkaInput, 
                                    List<String> errors, List<String> warnings) {
        // Topic validation (using listenTopics)
        if (kafkaInput.listenTopics() == null || kafkaInput.listenTopics().isEmpty()) {
            errors.add(prefix + "Must have at least one topic");
        } else {
            for (String topic : kafkaInput.listenTopics()) {
                if (topic == null || topic.isBlank()) {
                    errors.add(prefix + "Topic name cannot be null or blank");
                }
            }
            
            if (kafkaInput.listenTopics().size() > 10) {
                warnings.add(prefix + "Subscribing to many topics (" + kafkaInput.listenTopics().size() + 
                           ") may impact performance");
            }
        }
        
        // Consumer group validation (now optional in config)
        if (kafkaInput.consumerGroupId() != null && kafkaInput.consumerGroupId().isBlank()) {
            warnings.add(prefix + "Consumer group ID is blank - the engine will generate a default");
        }
        
        // Kafka consumer properties validation
        if (kafkaInput.kafkaConsumerProperties() != null) {
            Map<String, String> props = kafkaInput.kafkaConsumerProperties();
            
            // Check for common properties
            String maxPollRecords = props.get("max.poll.records");
            if (maxPollRecords != null) {
                try {
                    int value = Integer.parseInt(maxPollRecords);
                    if (value < 1) {
                        errors.add(prefix + "max.poll.records must be at least 1");
                    } else if (value > 5000) {
                        warnings.add(prefix + "Very high max.poll.records (" + value + 
                                   ") may cause memory issues");
                    }
                } catch (NumberFormatException e) {
                    errors.add(prefix + "max.poll.records must be a valid integer");
                }
            }
            
            // Check session timeout
            String sessionTimeout = props.get("session.timeout.ms");
            if (sessionTimeout != null) {
                try {
                    int value = Integer.parseInt(sessionTimeout);
                    if (value < 6000) {
                        warnings.add(prefix + "session.timeout.ms less than 6 seconds may cause frequent rebalances");
                    } else if (value > 300000) {
                        warnings.add(prefix + "session.timeout.ms over 5 minutes may delay failure detection");
                    }
                } catch (NumberFormatException e) {
                    errors.add(prefix + "session.timeout.ms must be a valid integer");
                }
            }
        }
    }
    
    private void validateKafkaTransport(String prefix, KafkaTransportConfig kafkaTransport,
                                       List<String> errors, List<String> warnings) {
        // Topic validation
        if (kafkaTransport.topic() == null || kafkaTransport.topic().isBlank()) {
            errors.add(prefix + "Topic is required");
        }
        
        // Batch size validation
        LOG.debugf("Validating batch size: %s", kafkaTransport.batchSize());
        if (kafkaTransport.batchSize() != null) {
            if (kafkaTransport.batchSize() < 1) {
                LOG.debugf("Batch size %d is less than 1, adding error", kafkaTransport.batchSize());
                errors.add(prefix + "Batch size must be at least 1");
            } else if (kafkaTransport.batchSize() > 1048576) { // 1MB
                warnings.add(prefix + "Very large batch size (" + kafkaTransport.batchSize() + 
                           " bytes) may impact latency");
            }
        }
        
        // Linger ms validation
        LOG.debugf("Validating linger ms: %s", kafkaTransport.lingerMs());
        if (kafkaTransport.lingerMs() != null) {
            if (kafkaTransport.lingerMs() < 0) {
                LOG.debugf("Linger ms %d is negative, adding error", kafkaTransport.lingerMs());
                errors.add(prefix + "Linger ms cannot be negative");
            } else if (kafkaTransport.lingerMs() > 1000) {
                warnings.add(prefix + "High linger ms (" + kafkaTransport.lingerMs() + 
                           "ms) will increase latency");
            }
        }
        
        // Compression type validation
        if (kafkaTransport.compressionType() != null && !kafkaTransport.compressionType().isBlank()) {
            String compression = kafkaTransport.compressionType().toLowerCase();
            if (!compression.matches("none|gzip|snappy|lz4|zstd")) {
                errors.add(prefix + "Invalid compression type '" + kafkaTransport.compressionType() + 
                         "' - must be one of: none, gzip, snappy, lz4, zstd");
            }
        }
        
        // Kafka producer properties validation
        if (kafkaTransport.kafkaProducerProperties() != null) {
            Map<String, String> props = kafkaTransport.kafkaProducerProperties();
            
            // Check for retention.ms (topic config, not producer config)
            if (props.containsKey("retention.ms")) {
                warnings.add(prefix + "retention.ms is a topic configuration, not a producer property");
            }
            
            // Check for partitions (topic config, not producer config)
            if (props.containsKey("partitions")) {
                warnings.add(prefix + "partitions is a topic configuration, not a producer property");
            }
        }
    }
    
    private void validateGrpcTransport(String prefix, GrpcTransportConfig grpcTransport,
                                      List<String> errors, List<String> warnings) {
        // Service name validation
        if (grpcTransport.serviceName() == null || grpcTransport.serviceName().isBlank()) {
            errors.add(prefix + "Service name is required");
        }
        
        // gRPC client properties validation
        if (grpcTransport.grpcClientProperties() != null) {
            Map<String, String> props = grpcTransport.grpcClientProperties();
            
            // Check timeout property
            String timeout = props.get("timeout");
            if (timeout != null) {
                try {
                    int value = Integer.parseInt(timeout);
                    if (value < 100) {
                        warnings.add(prefix + "Very short timeout (" + value + 
                                   "ms) may cause unnecessary failures");
                    } else if (value > 300000) { // 5 minutes
                        warnings.add(prefix + "Very long timeout (" + (value / 1000) + 
                                   " seconds) may delay error detection");
                    }
                } catch (NumberFormatException e) {
                    errors.add(prefix + "timeout must be a valid integer in milliseconds");
                }
            }
            
            // Check retry property
            String retry = props.get("retry");
            if (retry != null) {
                try {
                    int value = Integer.parseInt(retry);
                    if (value < 0) {
                        errors.add(prefix + "retry count cannot be negative");
                    } else if (value > 10) {
                        warnings.add(prefix + "High retry count (" + value + ") may cause long delays");
                    }
                } catch (NumberFormatException e) {
                    errors.add(prefix + "retry must be a valid integer");
                }
            }
        }
    }
    
    @Override
    public int getPriority() {
        return 350; // Run after basic validation but before complex validation
    }
    
    @Override
    public String getValidatorName() {
        return "TransportConfigValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Basic transport configuration needed in all modes
        return Set.of(ValidationMode.PRODUCTION, ValidationMode.DESIGN, ValidationMode.TESTING);
    }
}