package io.pipeline.model.validation.validators;

import io.pipeline.api.model.*;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationMode;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates that there are no circular dependencies within a pipeline.
 * Uses JGraphT to build a directed graph of pipeline steps and detect cycles.
 */
@ApplicationScoped
public class IntraPipelineLoopValidator implements PipelineConfigValidator {
    private static final Logger LOG = Logger.getLogger(IntraPipelineLoopValidator.class);
    private static final int MAX_CYCLES_TO_REPORT = 10;
    
    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        PipelineConfig config = (PipelineConfig) validatable;
        if (config == null || config.pipelineSteps() == null || config.pipelineSteps().isEmpty()) {
            return ValidationResultFactory.success();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Create a directed graph to represent the pipeline
        Graph<String, DefaultEdge> pipelineStepGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        
        // Add all steps as vertices in the graph
        for (Map.Entry<String, PipelineStepConfig> stepEntry : config.pipelineSteps().entrySet()) {
            String stepKey = stepEntry.getKey();
            PipelineStepConfig step = stepEntry.getValue();
            
            if (step != null && step.stepName() != null && !step.stepName().isBlank()) {
                if (!stepKey.equals(step.stepName())) {
                    // This is not directly related to loop detection, so we'll log it as a warning
                    // but not fail the validation
                    LOG.warnf("Pipeline '%s', Step key '%s' does not match stepName '%s'.",
                            config.name(), stepKey, step.stepName());
                    warnings.add(String.format("Step key '%s' does not match stepName '%s'.",
                            stepKey, step.stepName()));
                }
                pipelineStepGraph.addVertex(step.stepName());
            } else {
                errors.add(String.format("Pipeline '%s' contains a step with a null/blank ID or a null step object for key '%s'. Skipping for loop detection graph.",
                        config.name(), stepKey));
            }
        }

        // Add edges between steps based on Kafka connections
        for (PipelineStepConfig publishingStep : config.pipelineSteps().values()) {
            if (publishingStep == null || publishingStep.stepName() == null || publishingStep.stepName().isBlank() || publishingStep.outputs() == null) {
                continue;
            }

            for (Map.Entry<String, PipelineStepConfig.OutputTarget> outputEntry : publishingStep.outputs().entrySet()) {
                PipelineStepConfig.OutputTarget outputTarget = outputEntry.getValue();

                if (outputTarget != null && outputTarget.transportType() == TransportType.KAFKA && outputTarget.kafkaTransport() != null) {
                    KafkaTransportConfig pubKafkaConfig = outputTarget.kafkaTransport();
                    if (pubKafkaConfig.topic() == null || pubKafkaConfig.topic().isBlank()) {
                        continue;
                    }

                    String publishedTopicName = resolvePattern(
                            pubKafkaConfig.topic(),
                            publishingStep,
                            config.name()
                    );

                    if (publishedTopicName == null || publishedTopicName.isBlank()) {
                        continue;
                    }

                    for (PipelineStepConfig listeningStep : config.pipelineSteps().values()) {
                        if (listeningStep == null || listeningStep.stepName() == null || listeningStep.stepName().isBlank() || listeningStep.kafkaInputs() == null) {
                            continue;
                        }

                        // Check if listeningStep consumes the publishedTopicName
                        boolean listensToPublishedTopic = false;
                        for (KafkaInputDefinition inputDef : listeningStep.kafkaInputs()) {
                            if (inputDef.listenTopics() != null) {
                                for (String listenTopicPattern : inputDef.listenTopics()) {
                                    String resolvedListenTopic = resolvePattern(
                                            listenTopicPattern,
                                            listeningStep,
                                            config.name()
                                    );
                                    if (publishedTopicName.equals(resolvedListenTopic)) {
                                        listensToPublishedTopic = true;
                                        break;
                                    }
                                }
                            }
                            if (listensToPublishedTopic) break;
                        }

                        if (listensToPublishedTopic) {
                            if (!pipelineStepGraph.containsVertex(publishingStep.stepName()) ||
                                    !pipelineStepGraph.containsVertex(listeningStep.stepName())) {
                                LOG.warnf("Vertex missing for intra-pipeline edge: %s -> %s in pipeline %s. This should not happen if vertices were added correctly.",
                                        publishingStep.stepName(), listeningStep.stepName(), config.name());
                                continue;
                            }
                            try {
                                if (!pipelineStepGraph.containsEdge(publishingStep.stepName(), listeningStep.stepName())) {
                                    pipelineStepGraph.addEdge(publishingStep.stepName(), listeningStep.stepName());
                                    LOG.debugf("Added intra-pipeline edge from '%s' to '%s' via topic '%s' in pipeline '%s'",
                                            publishingStep.stepName(), listeningStep.stepName(), publishedTopicName, config.name());
                                }
                            } catch (IllegalArgumentException e) {
                                errors.add(String.format(
                                        "Error building graph for pipeline '%s': Could not add edge between '%s' and '%s'. Error: %s",
                                        config.name(), publishingStep.stepName(), listeningStep.stepName(), e.getMessage()));
                                LOG.warnf("Error adding edge to graph for pipeline %s: %s", config.name(), e.getMessage());
                            }
                        }
                    }
                }
            }
        }

        // Detect cycles in the graph
        if (pipelineStepGraph.vertexSet().size() > 0 && !pipelineStepGraph.edgeSet().isEmpty()) {
            JohnsonSimpleCycles<String, DefaultEdge> cycleFinder = new JohnsonSimpleCycles<>(pipelineStepGraph);
            List<List<String>> cycles = cycleFinder.findSimpleCycles();

            if (!cycles.isEmpty()) {
                LOG.warnf("Found %d simple intra-pipeline cycle(s) in pipeline '%s'. Reporting up to %d.",
                        cycles.size(), config.name(), MAX_CYCLES_TO_REPORT);
                for (int i = 0; i < Math.min(cycles.size(), MAX_CYCLES_TO_REPORT); i++) {
                    List<String> cyclePath = cycles.get(i);
                    String pathString = String.join(" -> ", cyclePath);
                    if (!cyclePath.isEmpty()) {
                        pathString += " -> " + cyclePath.get(0);
                    }
                    String errorMessage = String.format(
                            "Detected a loop in pipeline '%s': %s",
                            config.name(), pathString);
                    LOG.infof("[DEBUG_LOG] Adding error message: %s", errorMessage);
                    LOG.infof("[DEBUG_LOG] Cycle path: %s", cyclePath);
                    LOG.infof("[DEBUG_LOG] Expected error for test: Detected a loop in pipeline 'pipeline-with-direct-loop': step-a -> step-b -> step-a");
                    errors.add(errorMessage);
                }
                if (cycles.size() > MAX_CYCLES_TO_REPORT) {
                    errors.add(String.format(
                            "[%s] Pipeline '%s' has more than %d loops (%d total). Only the first %d are reported.",
                            getValidatorName(), config.name(), MAX_CYCLES_TO_REPORT, cycles.size(), MAX_CYCLES_TO_REPORT));
                }
            } else {
                LOG.debugf("No intra-pipeline loops detected in pipeline: %s", config.name());
            }
        } else {
            LOG.debugf("Intra-pipeline step graph for pipeline '%s' is empty or has no edges. No loop detection performed.", config.name());
        }

        return errors.isEmpty() ? ValidationResultFactory.success() : ValidationResultFactory.failure(errors, warnings);
    }

    private String resolvePattern(String topicStringInConfig, PipelineStepConfig step, String pipelineName) {
        if (topicStringInConfig == null || topicStringInConfig.isBlank()) {
            return null;
        }
        String stepNameForResolve = (step != null && step.stepName() != null) ? step.stepName() : "unknown-step";

        String resolved = topicStringInConfig
                .replace("${pipelineName}", pipelineName)
                .replace("${stepName}", stepNameForResolve);

        if (resolved.contains("${")) {
            LOG.debugf("Topic string %s for step %s in pipeline %s could not be fully resolved: %s",
                    topicStringInConfig, stepNameForResolve, pipelineName, resolved);
            return resolved;
        }
        return resolved;
    }
    
    @Override
    public int getPriority() {
        return 600; // Run after reference validation
    }
    
    @Override
    public String getValidatorName() {
        return "IntraPipelineLoopValidator";
    }
    
    @Override
    public Set<ValidationMode> supportedModes() {
        // Loop detection is important for design and production but can be relaxed for testing
        return Set.of(ValidationMode.PRODUCTION, ValidationMode.DESIGN);
    }
}