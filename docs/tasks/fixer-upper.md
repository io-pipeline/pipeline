# Schema Compliance Validator Enhancement

**Date:** 2025-07-17

## Task Description

Enhance the Schema Compliance Validator to provide more helpful feedback when validating pipeline configurations, particularly when a cluster configuration is missing. The validator should not only identify validation errors but also suggest potential fixes and show what further validation issues might exist after applying those fixes.

## Requirements

1. Create a `createSimpleClusterOnlySetup()` method in `MockPipelineGenerator` that returns a minimal valid cluster configuration
2. Enhance the `SchemaComplianceValidator` to:
   - Detect when a cluster is missing from a pipeline configuration
   - Suggest adding a default cluster in the error message
   - Continue validation to show further violations even when the cluster is missing
3. Implement a `suggestFixes()` method that can recursively apply fixes and continue validation
4. Make the recursion depth configurable to balance between comprehensive validation feedback and performance

## Implementation Approach

### 1. Add `createSimpleClusterOnlySetup()` to MockPipelineGenerator

Create a method that returns a minimal valid `PipelineClusterConfig` with default values:

```java
public static PipelineClusterConfig createSimpleClusterOnlySetup() {
    return new PipelineClusterConfig(
            "default-cluster",
            null,  // pipelineGraphConfig can be null
            null,  // pipelineModuleMap can be null
            null,  // defaultPipelineName can be null
            Collections.emptySet(),  // allowedKafkaTopics
            Collections.emptySet()   // allowedGrpcServices
    );
}
```

### 2. Enhance SchemaComplianceValidator

Modify the validator to include configurable recursion depth and fix suggestions:

1. Add a configurable recursion depth parameter
2. Implement a `suggestFixes()` method that can recursively apply fixes
3. Enhance the `validate()` method to include fix suggestions in the validation result

### 3. Configuration Options

Make the recursion depth configurable through:
- Constructor parameter with a default value
- Application property (optional enhancement)
- Builder pattern (optional enhancement)

## Testing Strategy

1. Test with a valid pipeline configuration (should pass validation)
2. Test with a pipeline missing a cluster (should fail validation but suggest adding a default cluster)
3. Test with multiple validation errors (should show all errors and suggest fixes)
4. Test with different recursion depths:
   - Depth = 0 (no suggestions)
   - Depth = 1 (only immediate fixes)
   - Depth > 1 (multiple levels of fixes)

## Acceptance Criteria

1. The `createSimpleClusterOnlySetup()` method creates a valid minimal cluster configuration
2. The validator correctly identifies when a cluster is missing
3. The validator suggests adding a default cluster when appropriate
4. The validator shows further validation errors that would occur even if the cluster was added
5. The recursion depth is configurable and prevents excessive recursion
6. All tests pass with the enhanced validator

## Implementation Notes

- The approach should be limited to a reasonable recursion depth to avoid stack overflow issues
- The validator should provide helpful feedback to users by showing all potential issues at once
- The implementation should maintain backward compatibility with existing code