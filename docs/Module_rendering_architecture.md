# Module Frontend Rendering Architecture

This document describes the technical architecture of the Pipeline Engine's module frontend rendering system.

## System Overview

The Pipeline Engine uses a **schema-driven, reference implementation approach** to provide consistent frontend experiences across all modules while maintaining flexibility for developers.

## Core Components

### 1. Pipeline Engine
- **Central orchestrator** for all module interactions
- **Renders Config Cards** using Vue.js components 
- **Manages module lifecycle** and communication
- **Provides unified interface** for all pipeline operations

### 2. Module Frontends (Reference Implementation)
- **Vue.js + JSONForms** based configuration interfaces
- **Auto-generated from OpenAPI 3.1** schemas
- **Identical pattern** across all modules (chunker, parser, etc.)
- **Production components** used by the Pipeline Engine

### 3. Test Proxy (Development Tool)
- **Container-based testing** environment
- **Schema validation** and form rendering preview
- **Identical to engine rendering** - what you see is what you get
- **Available for external developers** building non-Java modules

## Architecture Principles

### Schema-Driven Development
```
OpenAPI 3.1 Schema → JSONForms → Vue Component → Engine Integration
```

- **Single source of truth**: Module's OpenAPI schema
- **Auto-generation**: No manual form coding required
- **Consistency**: Identical rendering across all modules
- **Validation**: Schema-based validation on frontend and backend

### Reference Implementation Pattern
```
Internal Modules (Java/Quarkus) ←→ Engine ←→ External Modules (Any Language)
                ↓                              ↓
        Reference Frontend              Test Proxy
```

- **Internal modules** (chunker, parser) establish the pattern
- **External modules** follow the same OpenAPI standards
- **Engine always uses** the same Vue.js rendering components
- **Test proxy** ensures external modules render identically

### Pragmatic Standards
- **Not 100% generic** - optimized for our use cases
- **OpenAPI extensions** handle edge cases while staying compliant
- **Documented conventions** - clear guidelines for developers
- **Flexible customization** - Demo tabs can be module-specific

## Technical Stack

### Frontend Technology
- **Vue 3.5.13** - Modern reactive framework
- **JSONForms 3.3.0** - Schema-driven form generation
- **Vite 6.2.6** - Fast build tool and dev server
- **Axios 1.7.2** - HTTP client for API communication

### Backend Integration
- **Quarkus RESTEasy** - REST API endpoints
- **Jackson** - JSON serialization/deserialization
- **OpenAPI 3.1** - Schema generation and validation
- **gRPC** - Internal service communication

### Build Integration
- **Quinoa** - Quarkus extension for Node.js integration
- **Gradle** - Build system with frontend compilation
- **Docker** - Containerized deployment

## Component Architecture

### Config Card Structure
```
Config Card (Generic)
├── Schema Loading (/api/{module}/service/config)
├── JSONForms Rendering (Auto-generated)
├── Form Submission (/api/{module}/service/process-json)
└── Results Display (Standardized format)
```

### Module Frontend Structure
```
Module Frontend
├── Config Card (Generic - used by engine)
├── Demo Documents Tab (Module-specific)
├── Metadata Dashboard (Module-specific)  
└── File Upload (Module-specific)
```

### Data Flow
```
1. Frontend → GET /api/{module}/service/config → OpenAPI Schema
2. JSONForms → Auto-generate form from schema
3. User → Fill form → JSON data
4. Frontend → POST /api/{module}/service/process-json → Processing
5. Backend → Validate JSON → Call gRPC service → Return results
6. Frontend → Display standardized response
```

## API Standards

### Required Endpoints

#### Schema Endpoint
```http
GET /api/{module}/service/config
Content-Type: application/json

Response: OpenAPI 3.1 JSON Schema (JSON Schema Draft 7 compatible)
```

#### Processing Endpoint  
```http
POST /api/{module}/service/process-json
Content-Type: application/json

Request:
{
  "text": "Content to process",
  "config": {
    "config_id": "example-config",
    "processingOptions": { ... }
  }
}

Response:
{
  "success": true,
  "outputDoc": { ... },
  "processorLogs": [ ... ]
}
```

### Optional Endpoints (Module-specific)
- Demo documents: `GET /api/{module}/service/demo/documents`
- File upload: `POST /api/{module}/service/parse-file`
- Health check: `GET /api/{module}/service/health`

## Development Workflow

### Internal Module Development
1. **Create OpenAPI 3.1 config record** with proper annotations
2. **Implement required REST endpoints** (config, process-json)
3. **Copy Vue.js frontend structure** from reference modules
4. **Test with JSONForms rendering** locally
5. **Validate with integration tests**

### External Module Development
1. **Design OpenAPI 3.1 schema** for your module config
2. **Implement schema and processing endpoints** in your language
3. **Test with reference proxy** to see engine rendering
4. **Iterate on schema** based on form appearance
5. **Deploy with confidence** - proxy matches engine exactly

### Engine Integration
1. **Engine discovers modules** via service registry
2. **Fetches schema** from module's config endpoint
3. **Renders Config Card** using reference Vue components
4. **Submits forms** to module's processing endpoint
5. **Displays results** in standardized format

## Extension System

### OpenAPI Extensions (x-* properties)
- **Rendering hints**: Component types, ordering, labels
- **Validation**: Custom messages, help text
- **Behavior**: Hidden fields, read-only properties
- **Schema compliant**: Standard OpenAPI extension mechanism

### Vue Component Extensions
- **Custom renderers**: For specialized input types
- **Theme customization**: Consistent with engine styling
- **Validation hooks**: Enhanced user experience
- **Progressive enhancement**: Graceful degradation

## Deployment Architecture

### Development Mode
```
Module (Quarkus Dev) → Quinoa → Vue Dev Server → Live Reload
```

### Production Mode  
```
Module (JAR) → Quinoa → Built Vue Assets → Served from JAR
```

### Engine Integration Mode
```
Engine → Module gRPC → Module REST → Vue Components (Embedded)
```

## Testing Strategy

### Unit Testing
- **Schema validation** against JSON Schema Draft 7
- **API endpoint** testing with various inputs
- **Vue component** testing with different schemas

### Integration Testing
- **Full form rendering** with real schemas
- **End-to-end submission** through processing pipeline
- **Error handling** and validation display

### Reference Testing
- **Proxy container** testing for external modules
- **Cross-module consistency** validation
- **Engine integration** verification

## Future Considerations

### Scalability
- **Schema caching** for performance
- **Component lazy loading** for large modules
- **Progressive enhancement** for complex forms

### Extensibility  
- **Plugin system** for custom renderers
- **Theme system** for different engine deployments
- **Internationalization** support

### External Integration
- **REST API** for non-gRPC modules
- **WebSocket** support for real-time updates
- **OAuth integration** for secure access

## Migration Path

### From Custom Frontends
1. **Extract form fields** → OpenAPI schema properties
2. **Replace custom endpoints** → Standard schema/processing endpoints  
3. **Remove custom components** → JSONForms auto-generation
4. **Test with proxy** → Validate rendering
5. **Deploy with confidence** → Engine handles the rest

### Legacy Support
- **Gradual migration** - module by module
- **Backward compatibility** - old endpoints can coexist
- **Feature parity** - ensure no functionality loss
- **User experience** - maintain familiar workflows