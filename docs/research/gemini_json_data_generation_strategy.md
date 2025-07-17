# JSON Data Generation and Schema Validation Strategy

## 1. Introduction

This document outlines a comprehensive strategy for generating test data and ensuring schema compliance for the pipeline configuration system. The primary goal is to establish a robust foundation for testing the validation layer, while also enabling parallel development of the core pipeline engine and a future front-end management interface.

This strategy is divided into two main phases:

1.  **Mock Data Generation:** Programmatically creating a wide range of pipeline configurations in Java to thoroughly test the validators.
2.  **Realistic Data Generation:** Leveraging module-specific JSON schemas to generate semantically meaningful and realistic pipeline configurations, a task well-suited for an LLM.

Underpinning this strategy is a foundational validation step to ensure all configurations adhere to a formal JSON schema before any logical validation is attempted.

## 2. Foundational Schema Validation

To ensure a baseline of data quality and structural integrity, a new high-priority validator should be implemented:

*   **`SchemaComplianceValidator`:** This validator will be the first in the chain. Its sole responsibility is to validate the incoming JSON configuration against a formal JSON Schema v7 definition. This provides several key benefits:
    *   **Early Error Detection:** Catches malformed data and structural errors before they reach the more complex logical validators.
    *   **Clear Feedback:** Provides precise error messages based on the schema definition, making it easy for developers (and future UIs) to identify and fix issues.
    *   **Contract Enforcement:** The JSON schema acts as a formal contract for the pipeline configuration structure.

**Recommendation:** Use a library like `com.networknt:json-schema-validator` to implement this validator.

## 3. Phase 1: Mock Data Generation

This phase focuses on creating a robust suite of test data to validate the validators themselves. The key is to generate this data programmatically in Java to avoid the tedious and error-prone process of writing complex JSON by hand.

### 3.1. The `MockPipelineGenerator` Utility

A utility class, `MockPipelineGenerator.java`, should be created in the `data-util` library. This class will be responsible for constructing pipeline configuration objects (`PipelineConfig`, `PipelineClusterConfig`, etc.) in Java, which can then be serialized to JSON for testing.

```java
// In libraries/data-util/src/main/java/io/pipeline/data/util/json/MockPipelineGenerator.java

public class MockPipelineGenerator {

    // Generates a simple, valid pipeline with a few steps
    public static PipelineConfig createSimpleLinearPipeline() { /* ... */ }

    // Generates a pipeline with a direct, two-step loop
    public static PipelineConfig createPipelineWithDirectLoop() { /* ... */ }

    // Generates a cluster with two pipelines that loop through a shared Kafka topic
    public static PipelineClusterConfig createClusterWithInterLoop() { /* ... */ }

    // Generates a pipeline with a naming convention violation
    public static PipelineConfig createPipelineWithNamingViolation() { /* ... */ }

    // Generates a pipeline with an orphaned step
    public static PipelineConfig createPipelineWithOrphanedStep() { /* ... */ }

    // ... and so on for every validation rule and edge case
}
```

This generator will be used in the unit and integration tests for the validators to ensure they are working correctly.

## 4. Phase 2: Realistic Data Generation

Once the validators have been thoroughly tested with mock data, the next step is to generate more realistic pipeline configurations that use the actual modules you are developing.

### 4.1. Schema-Aware Generation

This phase will leverage the fact that each module will have its own JSON schema for its `customConfig`. These schemas will be stored in Consul and can be fetched at runtime.

### 4.2. LLM-Powered Configuration Generation

Generating meaningful and realistic `customConfig` JSON for a variety of modules is a perfect task for an LLM. A new utility, `RealisticConfigGenerator`, can be created to facilitate this process. The workflow would be as follows:

1.  **Fetch Schema:** The utility fetches a module's JSON schema from Consul.
2.  **Generate Prompt:** A prompt is constructed for the LLM, including the JSON schema and a request to generate a realistic configuration that conforms to it.
3.  **LLM Generates Config:** The LLM generates a valid and semantically plausible JSON object for the `customConfig`.

For example, given the schema for an `opensearch-sink` module, the LLM could generate a configuration with realistic hostnames, index patterns, and authentication settings (using placeholders for secrets).

### 4.3. Assembling Realistic Pipelines

The generated realistic configurations can then be used to assemble full pipeline and cluster configurations using the same Java-based generator approach from Phase 1. This will allow you to create a library of default, ready-to-use pipelines that can serve as examples for developers and as a basis for more complex configurations.

## 5. Enabling Parallel Development

This two-phased approach to data generation is key to enabling parallel development:

*   **The Generated JSON is the Contract:** The JSON output from the generators serves as a stable, well-defined contract.
*   **Front-End Development:** The front-end team can use the generated JSON to build and test the visual pipeline editor without needing a running back-end engine.
*   **Engine Development:** The core engine team can use the same JSON to test the pipeline execution and routing logic.
*   **gRPC Services:** The gRPC stubs in `grpc-stubs` provide the API contract that ties these services together.

By decoupling the development of the front-end, back-end, and validation logic, this strategy allows for a more efficient and parallel workflow, significantly accelerating the development process.
