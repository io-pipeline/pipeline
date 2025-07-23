package io.pipeline.model.validation.validators;

import io.pipeline.api.events.ServiceListUpdatedEvent;
import io.pipeline.api.model.*;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.test.support.PipelineConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepReferenceValidatorUnitNoBaseTest {

    private StepReferenceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StepReferenceValidator();
        // Simulate the initial state where no services are known
        validator.onServiceListUpdate(new ServiceListUpdatedEvent(Set.of()));
    }

    @Test
    void testEmptyConfigIsValid() {
        PipelineConfig config = new PipelineConfig("empty-pipeline", Map.of());
        ValidationResult result = validator.validate(config);
        assertTrue(result.valid());
    }

    @Test
    void testValidStepReference() {
        // Arrange
        validator.onServiceListUpdate(new ServiceListUpdatedEvent(Set.of("service-a", "service-b")));
        PipelineConfig config = PipelineConfigFactory.createSimpleLinearPipeline("service-a", "service-b");

        // Act
        ValidationResult result = validator.validate(config);

        // Assert
        assertTrue(result.valid());
    }

    @Test
    void testInvalidStepReference() {
        // Add the step's own service to the allowed list to ensure we're only testing the invalid output reference
        validator.onServiceListUpdate(new ServiceListUpdatedEvent(Set.of("service-a")));

        PipelineConfig config = new PipelineConfig("invalid-ref-pipeline", Map.of(
                "step-1",
                new PipelineStepConfig(
                        "step-1",
                        StepType.CONNECTOR,
                        "Should be an invalid step reference",
                        null,
                        null,
                        null,
                        // The validator checks the serviceName within GrpcTransportConfig for internal routing.
                        // To test for a non-existent step, we must put the invalid step name here.
                        Map.of("output-1", new PipelineStepConfig.OutputTarget("step-2", TransportType.GRPC, new GrpcTransportConfig("step-2", null), null)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new PipelineStepConfig.ProcessorInfo("service-a")
                )
        ));

        ValidationResult result = validator.validate(config);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("non-existent target step 'step-2'")));
    }

    @Test
    void testUnregisteredService() {
        PipelineConfig config = new PipelineConfig("unregistered-service-pipeline", Map.of(
                "step-1",
                new PipelineStepConfig(
                        "step-1",
                        StepType.CONNECTOR,
                        "Generic step 1 connector",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new PipelineStepConfig.ProcessorInfo("unregistered-service")
                )
        ));

        ValidationResult result = validator.validate(config);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("references gRPC service 'unregistered-service' which is not registered")));
    }

    @Test
    void testServiceListUpdate() {
        // Initially, the service is not registered
        PipelineConfig config = new PipelineConfig("service-list-update-pipeline", Map.of(
                "step-1",
                new PipelineStepConfig(
                        "step-1",
                        StepType.CONNECTOR,
                        "Service list fake mock connector type for step 1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new PipelineStepConfig.ProcessorInfo("new-service")
                )
        ));

        ValidationResult initialResult = validator.validate(config);
        assertFalse(initialResult.valid());

        // Simulate a service update event
        validator.onServiceListUpdate(new ServiceListUpdatedEvent(Set.of("new-service")));

        ValidationResult updatedResult = validator.validate(config);
        assertTrue(updatedResult.valid(), "Validation should pass after the service list is updated");
    }
}
