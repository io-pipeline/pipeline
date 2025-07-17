package io.pipeline.model.validation.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.pipeline.api.validation.PipelineConfigValidatable;
import io.pipeline.api.validation.PipelineConfigValidator;
import io.pipeline.api.validation.ValidationResult;
import io.pipeline.common.validation.ValidationResultFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SchemaComplianceValidator implements PipelineConfigValidator {

    private final JsonSchema schema;
    private final ObjectMapper objectMapper;

    @Inject
    public SchemaComplianceValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        InputStream schemaStream = getClass().getResourceAsStream("/pipeline-schema.json");
        this.schema = factory.getSchema(schemaStream);
    }

    @Override
    public ValidationResult validate(PipelineConfigValidatable validatable) {
        try {
            JsonNode jsonNode = objectMapper.valueToTree(validatable);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (errors.isEmpty()) {
                return ValidationResultFactory.success();
            } else {
                List<String> errorMessages = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.toList());
                return ValidationResultFactory.failure(errorMessages);
            }
        } catch (Exception e) {
            return ValidationResultFactory.failure("Failed to validate against JSON schema: " + e.getMessage());
        }
    }

    @Override
    public int getPriority() {
        return 10; // Highest priority
    }

    @Override
    public String getValidatorName() {
        return "SchemaComplianceValidator";
    }
}
