package io.pipeline.validation.validators;

import io.pipeline.api.model.PipelineConfig;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.data.util.json.MockPipelineGenerator;
import io.pipeline.model.validation.validators.SchemaComplianceValidator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SchemaComplianceValidatorTest {

    @Inject
    SchemaComplianceValidator validator;

    @Test
    void validate_validPipeline_returnsSuccess() {
        PipelineConfig validPipeline = MockPipelineGenerator.createSimpleLinearPipeline();
        ValidationResult result = validator.validate(validPipeline);
        assertTrue(result.valid());
    }

    @Test
    void validate_pipelineWithSchemaViolation_returnsFailure() {
        PipelineConfig invalidPipeline = MockPipelineGenerator.createPipelineWithSchemaViolation();
        ValidationResult result = validator.validate(invalidPipeline);
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().getFirst().contains("name: null found, string expected"));
    }
}
