# RFC 002: Module Capabilities and Adaptive Forms

## Status
- **Date**: 2025-01-30
- **Status**: Draft
- **Author**: Pipeline Team

## Summary

This RFC proposes adding a capabilities system to ModuleRegistration that allows pipeline modules to declare their operational characteristics. These capabilities will be used by UI tools (particularly the dev tools) to adapt form interfaces based on the module's intended behavior, without constraining the actual pipeline execution.

## Motivation

Currently, the dev tools present the same PipeDoc editing interface regardless of the module's purpose. This creates a suboptimal user experience where:

1. Parser modules need file upload functionality prominently displayed
2. Transform modules don't need source document creation
3. Different modules "own" different parts of the PipeDoc (e.g., chunkers own SemanticResults)
4. Users see irrelevant fields for their current task

By adding capability hints, we can create adaptive forms that guide users appropriately while maintaining the flexibility of the pipeline architecture.

## Design

### Protobuf Changes

Add to `module_registration.proto`:

```protobuf
message ModuleRegistration {
  // ... existing fields ...
  Capabilities capabilities = 15;
}

message Capabilities {
  repeated CapabilityType types = 1;
  // Reserved for future capability-specific configuration
}

enum CapabilityType {
  CAPABILITY_TYPE_UNSPECIFIED = 0;  // Default behavior
  PARSER = 1;                       // Converts raw input to documents
  
  // Future capabilities (commented out until needed):
  // TRANSFORM = 2;                 // Document to document modifications  
  // EXTRACTOR = 3;                 // One document to many
  // CHUNKER = 4;                   // Splits content, owns SemanticResults
  // EMBEDDER = 5;                  // Creates embeddings, owns Embedding
  // ENRICHER = 6;                  // Adds metadata
  // SINK = 7;                      // Outputs to external systems
  // FILTER = 8;                    // Conditional routing
  // CONNECTOR_GIGO = 9;            // Simple data acceptance
  // CONNECTOR_SOURCE_AWARE = 10;   // Tracks source state  
  // CONNECTOR_EXPLORATORY = 11;    // Discovers data dynamically
}
```

### UI Behavior

The PipeDocEditor component will adapt based on declared capabilities:

#### Default (CAPABILITY_TYPE_UNSPECIFIED)
- Shows full PipeDoc editor as currently implemented
- All fields editable
- No special UI emphasis

#### PARSER Capability
- **Initial State**:
  - File upload component prominently displayed
  - Source-related fields (sourceUri, sourceMimeType) visible but empty
  - Other sections minimized or hidden
  
- **Post-Upload State**:
  - Blob data populated from file
  - sourceUri auto-filled with filename
  - sourceMimeType auto-detected
  - Blob fields become read-only
  - Document metadata fields become active
  - Title defaults to filename (without extension)

### Implementation Phases

1. **Phase 1: Parser Support**
   - Add Capabilities to ModuleRegistration proto
   - Modify PipeDocEditor to check module capabilities
   - Implement adaptive behavior for PARSER type
   - Create seed data through parser interface

2. **Phase 2: Progressive Enhancement**
   - Add capabilities only as modules are built
   - Each capability based on real UI/UX needs
   - Avoid speculative design

3. **Phase 3: Test Data Migration**
   - Use created documents to replace binary test files
   - Export/import test data sets
   - Reduce repository size

## Benefits

1. **Better UX**: Users see relevant fields for their task
2. **Flexible**: Capabilities are hints, not constraints
3. **Extensible**: Easy to add new capabilities as needed
4. **Clean**: Single adaptive form instead of multiple components
5. **Data-Driven**: UI behavior driven by module metadata

## Alternatives Considered

1. **Separate Components**: Create PipeDocSeedEditor, PipeDocStreamEditor, etc.
   - Rejected: Code duplication, maintenance burden

2. **Hard-Coded Module Types**: Define strict module categories
   - Rejected: Reduces flexibility, doesn't match pipeline philosophy

3. **UI-Only Configuration**: Configure forms separately from modules
   - Rejected: Separates related concerns, harder to maintain

## Future Considerations

1. **Capability Parameters**: The Capabilities message could include configuration:
   ```protobuf
   message Capabilities {
     repeated CapabilityType types = 1;
     map<string, google.protobuf.Any> config = 2;
   }
   ```

2. **Capability Versions**: As capabilities evolve, versioning may be needed

3. **Runtime Capability Discovery**: Modules could dynamically report capabilities

## Implementation Notes

- Start with minimal capability set
- Add capabilities based on actual implementation needs
- Keep the engine as pure orchestrator
- Capabilities influence UI only, not pipeline behavior