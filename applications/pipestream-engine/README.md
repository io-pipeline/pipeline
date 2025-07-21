# PipeStream Engine

**Status**: 🚧 **IN DEVELOPMENT** - Basic skeleton implemented, core routing logic needed

## Overview

The PipeStream Engine is the **central orchestrator** for the Pipeline Engine architecture. It implements a **network graph model** (not linear pipelines) where data flows between interconnected modules based on routing decisions, similar to DNS resolution.

## Critical Architecture Principles

### 🎯 **Core Rule: Modules Never Talk to Each Other Directly**
- All communication goes **through the Engine**
- Engine reads pipeline config, routes data between modules
- Modules are simple gRPC services focused on one task

### 🌐 **Network Graph Model**
- Data can enter at any node (usually CONNECTOR type)
- No explicit "initial steps" - the network graph "just exists"
- Routing decisions based on pipeline configuration and data content

### 📊 **Two Data Structures**
- **`PipeStream`**: Execution envelope (routing metadata, hop count, history)
- **`PipeDoc`**: Actual document/data being processed (body, embeddings, chunks)

### 🔧 **Module Types**
- **`CONNECTOR`**: Entry points bringing data into network (e.g., filesystem-crawler)
- **`PIPELINE`**: Processing nodes transforming data (e.g., parser, chunker, embedder)
- **`SINK`**: Exit points persisting/exporting data (e.g., opensearch-sink)

## Current Implementation

### ✅ **Completed**
- [x] Copied from draft module template
- [x] Updated configuration (port 38100, proper naming)
- [x] Added to settings.gradle
- [x] Added dependencies: consul-client, dynamic-grpc, pipeline-commons, etc.
- [x] **BUILD SUCCESSFUL** - Application compiles correctly
- [x] Created skeleton gRPC service implementations
- [x] Fixed import issues (PipeStream is in `io.pipeline.data.model`)

### 🏗️ **Current Skeleton Services**

#### `PipeStreamEngineImpl.java`
```java
@GrpcService
public class PipeStreamEngineImpl implements PipeStreamEngine {
    // 🔄 testPipeStream() - For testing/debugging pipeline flows
    // 🚀 processPipeAsync() - Main asynchronous processing method  
    // 📡 processPipeStream() - Streaming for high-throughput
}
```

#### `ConnectorEngineImpl.java`
```java  
@GrpcService
public class ConnectorEngineImpl implements ConnectorEngine {
    // 📥 processConnectorDoc() - Entry point for connectors to inject documents
}
```

### 🎯 **Next Critical Implementation Steps**

1. **Core Routing Logic** (HIGH PRIORITY)
   - Read pipeline configuration from Consul
   - Determine next step based on `target_step_name`
   - Use **dynamic-grpc library** to call target modules
   - Update PipeDoc with results and route to next step

2. **Pipeline Configuration Service**
   - Integrate with Consul to read pipeline definitions
   - Map connector types to pipeline configurations
   - Handle configuration hot-reloading

3. **Dynamic Module Discovery** 
   - Use **dynamic-grpc** to solve Quarkus limitation
   - Discover available modules via Consul/Registration Service
   - Create gRPC stubs at runtime (not compile-time)

4. **Error Handling & Monitoring**
   - Retry policies, failure routing
   - Hop count tracking, execution history
   - Performance metrics

## Technical Context

### 🔌 **Dynamic gRPC Challenge**
- **Problem**: Quarkus forces you to know gRPC service names at compile-time via config
- **Solution**: **dynamic-grpc library** allows runtime service discovery and stub creation
- **Usage**: `dynamicGrpcClient.findService("chunker").call(request)`

### 📋 **Configuration**
- **Port**: 38100 (different from modules which use 39100+)
- **Application Name**: pipestream-engine
- **Type**: orchestrator (not module)
- **Dependencies**: consul-client, dynamic-grpc, pipeline-commons, etc.

### 🗂️ **Proto Definitions**
- **engine_service.proto**: PipeStreamEngine service (3 methods)
- **connector_service.proto**: ConnectorEngine service (1 method)
- **pipeline_core_types.proto**: PipeStream, PipeDoc data structures

## Demo Goal 🎯

**Target**: End-to-end pipeline demonstration for tomorrow

### **Simple 3-Step Flow**
```
filesystem-crawler → pipestream-engine → chunker → pipestream-engine → echo-sink
```

1. **filesystem-crawler** sends `ConnectorRequest` to `ConnectorEngine`
2. **Engine** creates `PipeStream`, routes to **chunker** via `PipeStreamEngine`
3. **chunker** processes document, returns to **Engine**
4. **Engine** routes final result to **echo** (acting as sink)

### **Success Criteria**
- Document flows through complete pipeline
- Engine orchestrates all routing decisions
- No direct module-to-module communication
- Demonstrate network graph architecture

## Key Files & Context

### 📁 **Implementation Files**
- `/src/main/java/io/pipeline/engine/PipeStreamEngineImpl.java` - Core orchestrator
- `/src/main/java/io/pipeline/engine/ConnectorEngineImpl.java` - Connector entry point
- `/src/main/resources/application.properties` - Configuration

### 📚 **Reference Documentation**
- `/docs/Architecture_overview.md` - Network graph principles, core concepts
- `/docs/Pipeline_design.md` - Detailed pipeline configuration hierarchy

### 🔧 **Related Components**
- **libraries/dynamic-grpc** - Runtime gRPC client creation
- **libraries/consul-client** - Pipeline configuration reading
- **modules/chunker** - Working module with both algorithms (ready for integration)
- **applications/registration-service** - Module discovery and health

### ✅ **Validation System Status**
- JSON Schema v7 validation **SOLVED** with SchemaExtractorService
- Hardcoded service validation **DOCUMENTED** with TODOs for dynamic discovery
- chunker module **WORKING** with proper schema extraction

## Critical Implementation Notes

### 🚨 **Don't Repeat Past Mistakes**
- **NEVER** hardcode schemas - use Java-based OpenAPI generation
- **NEVER** implement linear pipeline thinking - it's a network graph
- **NEVER** let modules talk directly - everything through Engine
- **ALWAYS** use dynamic-grpc for runtime service discovery
- **ALWAYS** preserve the PipeStream/PipeDoc separation

### 🎯 **Focus Areas**
1. **Routing Logic**: The core engine orchestration
2. **Dynamic gRPC**: Using the library to call modules at runtime  
3. **Consul Integration**: Reading pipeline configurations
4. **Simple Demo**: Get basic flow working first

### 📋 **Validation Strategy**
- Use existing chunker module as first integration target
- Test with filesystem-crawler for document input
- Use echo module as simple sink for output
- Verify no direct module communication

## Running the Application

### Development Mode
```shell script
../../gradlew :applications:pipestream-engine:quarkusDev
```
Access at: http://localhost:38100

### Build
```shell script
../../gradlew :applications:pipestream-engine:build
```

### gRPC Services Available
- **PipeStreamEngine** on port 38100
- **ConnectorEngine** on port 38100 
- **gRPC Reflection** enabled for testing

## Current Status Summary

**✅ Infrastructure**: Application builds, basic services exist, dependencies correct  
**🚧 Implementation**: Need core routing logic, Consul integration, dynamic-grpc usage  
**🎯 Target**: Working demo pipeline by tomorrow  
**🔑 Key**: Focus on routing logic and dynamic service calling - that's the hard part

---

**Remember**: This is a **network graph orchestrator**, not a linear pipeline processor. The engine is the intelligent routing fabric that makes dynamic, configuration-driven data flows possible.
