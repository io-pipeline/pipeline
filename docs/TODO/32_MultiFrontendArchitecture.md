# Multi-Frontend Architecture for Pipeline Modules

## Executive Summary

This RFC outlines a comprehensive multi-frontend architecture that supports both internal Java/Quarkus modules and external modules written in any language. The strategy uses a progressive development approach with Tika parser as the reference implementation to validate the entire architecture before scaling across all modules.

## Problem Statement

### Current Challenges
1. **Inconsistent Frontend Development**: Existing modules (chunker, embedder, parser) have different frontend patterns
2. **Multi-Language Support Gap**: No clear path for Go/Python/etc developers to create modules with proper frontends
3. **Development-to-Production Friction**: Developers can't preview exactly how their modules will render in production
4. **Frontend Code Duplication**: Each module reimplements similar rendering logic

### Goals
1. **Consistent UX**: All modules use identical rendering components and patterns
2. **Multi-Language Support**: External developers can build modules in any gRPC-capable language
3. **Zero-Infrastructure Development**: Developers need minimal setup to preview and test their modules
4. **Production Security**: External modules can be wrapped with enterprise features without code changes

## Architecture Overview

### Four Frontend Types

#### 1. **Developer Frontend** (Standalone Node.js)
- **Purpose**: Development and testing tool for external developers
- **Technology**: Pure Node.js with gRPC client
- **Connection**: Direct host:port to module (e.g., `localhost:39102`)
- **Dependencies**: None (no Consul, no service discovery)
- **Features**:
  - Schema rendering preview using UniversalConfigCard
  - Functional testing with sample protobuf requests/responses
  - "What you see is what you get" experience

#### 2. **Native Quarkus Module Frontends** (Your internal modules)
- **Purpose**: Full-featured module frontends with demos
- **Technology**: Quarkus + Quinoa + Vue.js
- **Connection**: Built-in gRPC services
- **Features**:
  - Shared UniversalConfigCard components
  - Module-specific demo features
  - Full Quarkus enterprise features (metrics, health, auto-registration)
  - Consistent UX patterns

#### 3. **Proxy-Module Frontend** (Enterprise wrapper)
- **Purpose**: Production wrapper for external modules
- **Technology**: Quarkus + Quinoa + Vue.js
- **Connection**: Consul service discovery + gRPC proxy
- **Features**:
  - Same rendering as Developer Frontend
  - Full Quarkus enterprise features
  - Wraps "dumb" external modules with production capabilities

#### 4. **Pipeline Designer Frontend** (Engine integration)
- **Purpose**: Production pipeline creation interface
- **Technology**: Engine-integrated frontend
- **Connection**: Consul service discovery
- **Features**:
  - Identical UniversalConfigCard rendering
  - Module discovery and configuration
  - Pipeline composition interface

## Implementation Strategy

### Phase 1: Developer Frontend + Tika Parser (Reference Implementation)

**Objective**: Create and validate the standalone developer frontend experience using Tika parser as the test case.

#### Deliverables
1. **Standalone Node.js Project**
   ```
   frontends/developer-frontend/
   ├── package.json
   ├── src/
   │   ├── components/
   │   │   └── UniversalConfigCard.vue
   │   ├── grpc/
   │   │   └── client.js
   │   └── main.js
   └── sample-data/
       ├── tika-requests.proto.bin
       └── tika-responses.proto.bin
   ```

2. **Direct gRPC Integration**
   - Connect to Tika parser at `localhost:39104` (or configurable)
   - Schema extraction via gRPC call
   - Request/response testing with sample protobufs

3. **UniversalConfigCard Implementation**
   - Pure Vue.js component (no Quarkus dependencies)
   - JSONForms integration for schema rendering
   - Identical rendering logic to production

4. **Sample Data Package**
   - Pre-built protobuf request binaries for common document types
   - Expected response examples
   - Developer can immediately test without creating test data

#### Success Criteria
- [ ] External developer can run `npm start` and see Tika schema rendered
- [ ] Functional testing works with sample protobuf data
- [ ] Schema rendering matches what will appear in production
- [ ] Zero infrastructure dependencies (no Consul setup required)

### Phase 2: Convert Existing Native Modules (Consistency First)

**Objective**: Apply the validated frontend patterns to existing Quarkus modules to achieve consistent UX.

#### Target Modules
1. **Chunker** (port 39102) - Already partially converted
2. **Embedder** (port 39103) - Needs frontend consistency
3. **Parser** (port 39101) - Fix rendering issues
4. **Echo** (port 39100) - Reference implementation

#### Deliverables
1. **Shared Component Library**
   ```
   libraries/shared-ui/
   ├── src/
   │   ├── components/
   │   │   ├── UniversalConfigCard.vue
   │   │   ├── ModuleHeader.vue
   │   │   └── DemoTabs.vue
   │   └── utils/
   │       └── grpcClient.js
   ```

2. **Module Frontend Standardization**
   - Each module uses identical UniversalConfigCard
   - Consistent layout and navigation patterns
   - Module-specific demo tabs for additional features
   - Dynamic module names from application.properties

3. **Build Integration**
   - File copying strategy for shared components (proven working)
   - Consistent Quinoa dev server port allocation
   - Unified build process across all modules

#### Success Criteria
- [ ] All modules have identical Config Card rendering
- [ ] Module-specific features preserved in separate demo tabs
- [ ] Consistent UX patterns across all native modules
- [ ] No frontend contamination between modules

### Phase 3: Proxy-Module Implementation (Enterprise Wrapper)

**Objective**: Create the Proxy-Module that wraps external modules with enterprise features while preserving the validated frontend experience.

#### Architecture
```
External Module (Go/Python/etc) ←→ Proxy-Module (Quarkus) ←→ Pipeline Engine
                                        ↑
                                   Frontend (Vue.js)
```

#### Deliverables
1. **Proxy-Module Service**
   ```
   modules/proxy-module/
   ├── src/main/java/
   │   └── io/pipeline/proxy/
   │       ├── ProxyService.java
   │       ├── ExternalModuleClient.java
   │       └── config/ProxyConfig.java
   ├── src/main/ui-vue/
   │   └── [Same frontend as Developer Frontend]
   └── src/main/resources/
       └── application.properties
   ```

2. **Enterprise Feature Integration**
   - Auto-registration with Consul
   - Metrics collection and export
   - Health check proxying
   - Logging standardization
   - Security policy enforcement

3. **Frontend Reuse**
   - Same UniversalConfigCard from Phase 1
   - Same functional testing capabilities
   - Service discovery integration for production deployment

4. **Configuration System**
   - Point proxy at external module (host:port)
   - Module metadata passthrough
   - Schema proxying and caching

#### Success Criteria
- [ ] External module (Tika) works identically through proxy
- [ ] All enterprise features functional (metrics, health, registration)
- [ ] Frontend experience identical to Developer Frontend
- [ ] Production-ready security and isolation

### Phase 4: Complete Ecosystem Rollout

**Objective**: Establish the complete multi-language module ecosystem with all frontend types working together.

#### Deliverables
1. **Documentation Package**
   - External developer onboarding guide
   - Frontend component documentation
   - Proxy-Module deployment guide
   - Pipeline Designer integration guide

2. **Template Projects**
   - Go module template with Developer Frontend
   - Python module template with Developer Frontend
   - Generic gRPC service template

3. **Production Deployment**
   - Proxy-Module container images
   - Helm charts for Kubernetes deployment
   - CI/CD pipelines for external module integration

4. **Pipeline Designer Integration**
   - Engine discovers all module types via Consul
   - Identical rendering experience for all modules
   - Configuration validation and pipeline composition

#### Success Criteria
- [ ] External developers can create modules in any language
- [ ] All modules render identically in Pipeline Designer
- [ ] Production deployment supports both native and proxy modules
- [ ] Zero frontend code duplication across ecosystem

## Technical Specifications

### Shared Component Architecture

#### UniversalConfigCard Interface
```javascript
// Common interface across all frontend types
export default {
  props: {
    moduleEndpoint: String,      // Direct connection or proxy
    schemaEndpoint: String,      // gRPC method for schema
    processEndpoint: String,     // gRPC method for processing
    sampleData: Object,          // Pre-built test data
    debugMode: Boolean           // Enable request/response display
  },
  // ... implementation
}
```

#### gRPC Client Abstraction
```javascript
// Works with direct connection or service discovery
class ModuleClient {
  constructor(endpoint, discoveryType = 'direct') {
    // discoveryType: 'direct', 'consul', 'stork'
  }
  
  async getSchema() { /* ... */ }
  async processRequest(config, data) { /* ... */ }
}
```

### Development Workflow

#### For External Developers
1. **Setup**: `git clone frontend-template && npm install`
2. **Development**: Point to module at `localhost:port`
3. **Testing**: Use provided sample protobufs
4. **Validation**: See exact production rendering
5. **Deployment**: Module runs standalone or via Proxy-Module

#### For Internal Development
1. **Module Creation**: Copy existing module template
2. **Frontend**: Include shared-ui components
3. **Testing**: Quinoa dev mode with live reload
4. **Integration**: Auto-registration with Consul
5. **Production**: Native Quarkus deployment

## Migration Plan

### Current State Assessment
- **Chunker**: Partially converted to pure JSON, needs consistency
- **Parser**: Rendering issues with nested schemas  
- **Embedder**: No frontend currently
- **Echo**: Basic demo interface

### Phase 1 Implementation (4-6 weeks)
- Week 1-2: Developer Frontend + Tika integration
- Week 3-4: Functional testing with protobufs
- Week 5-6: Documentation and validation

### Phase 2 Implementation (3-4 weeks)  
- Week 1: Shared component library finalization
- Week 2-3: Convert all existing modules
- Week 4: Testing and consistency validation

### Phase 3 Implementation (4-6 weeks)
- Week 1-2: Proxy-Module service development
- Week 3-4: Enterprise feature integration
- Week 5-6: Production deployment testing

### Phase 4 Implementation (2-3 weeks)
- Week 1: Documentation and templates
- Week 2-3: Pipeline Designer integration

## Success Metrics

### Developer Experience
- [ ] Zero-infrastructure setup time for external developers
- [ ] Identical rendering between development and production
- [ ] Sub-5-minute module frontend creation time

### Consistency Metrics
- [ ] 100% shared component usage across all modules
- [ ] Zero frontend code duplication
- [ ] Identical UX patterns across module types

### Production Readiness
- [ ] External modules deployable with enterprise features
- [ ] Security isolation between modules
- [ ] Monitoring and observability for all module types

## Risk Mitigation

### Technical Risks
- **gRPC Client Compatibility**: Validate Node.js gRPC works with all target languages
- **Schema Evolution**: Ensure UniversalConfigCard handles schema changes gracefully
- **Performance**: Test frontend performance with large schemas

### Operational Risks
- **External Developer Adoption**: Provide comprehensive documentation and examples
- **Frontend Maintenance**: Establish clear ownership and update processes
- **Security Model**: Validate proxy module isolation in production environments

## Conclusion

This multi-frontend architecture solves the fundamental challenge of providing consistent, powerful frontend experiences across a heterogeneous module ecosystem. By using Tika parser as the reference implementation and prioritizing consistency across existing modules, we can validate the approach incrementally while solving immediate development pain points.

The progressive implementation strategy ensures that each phase builds on proven foundations, reducing risk while delivering immediate value to both internal development teams and external module creators.