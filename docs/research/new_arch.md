# New Pipeline Architecture - Complete Design Documentation

## Executive Summary

This document outlines a complete redesign of the pipeline system from a linear orchestration model to a **DNS-like graph network** where nodes exist independently and can be discovered/traversed without full graph awareness. The new architecture eliminates the central orchestrator bottleneck and enables true distributed processing.

## Core Architecture Principles

### 1. DNS-Like Node Resolution
- Each node has a globally unique ID: `{cluster_id}.{node_name}` (e.g., "prod.chunker-v1")
- Nodes can be discovered and invoked without knowing the full graph topology
- Similar to DNS where you can resolve `google.com` without knowing the entire internet structure

### 2. Graph-First Design
- Nodes exist independently in the network
- Processing flows are discovered dynamically through node-to-node communication
- No central orchestrator required for basic operations
- Engine becomes a routing helper, not a bottleneck

### 3. Dual Transport Model
- **Kafka**: Asynchronous, high-throughput, persistent messaging
- **gRPC**: Synchronous, low-latency, direct communication
- Nodes can communicate via either transport based on requirements

### 4. Clean Data Separation
- **PipeStream**: TCP-like packet header with routing metadata, hop tracking, trace IDs
- **PipeDoc**: Clean document payload with structured data, blobs, and search metadata
- **SearchMetadata**: Normalized metadata for search/discovery operations

## Key Data Structures

### PipeStream (TCP-like Header)
```protobuf
message PipeStream {
  string stream_id = 1;           // Unique stream identifier
  string trace_id = 2;            // Distributed tracing
  int32 hop_count = 3;            // Network hop tracking
  string source_node_id = 4;      // Origin node
  string current_node_id = 5;     // Current processing node
  string target_node_id = 6;      // Next destination
  map<string, string> routing_context = 7;  // Routing metadata
  repeated string processing_history = 8;   // Audit trail
  int64 created_timestamp = 9;    // Stream creation time
  int64 last_modified = 10;       // Last processing time
}
```

### PipeDoc (Clean Document Structure)
```protobuf
message PipeDoc {
  string doc_id = 1;              // Unique document identifier
  SearchMetadata search_metadata = 2;  // Normalized search fields
  repeated Blob blobs = 3;        // Binary/text content
  google.protobuf.Any structured_data = 4;  // Typed structured data
  map<string, string> processing_metadata = 5;  // Processing context
}
```

### SearchMetadata (Normalized Search Fields)
```protobuf
message SearchMetadata {
  string title = 1;
  string body = 2;
  repeated string keywords = 3;
  string content_type = 4;
  string language = 5;
  int64 content_length = 6;
  string source_url = 7;
  string author = 8;
  int64 created_date = 9;
  int64 modified_date = 10;
  map<string, string> custom_fields = 11;
}
```

### Blob (Enhanced Binary Content)
```protobuf
message Blob {
  string blob_id = 1;
  string content_type = 2;
  bytes data = 3;
  string encoding = 4;
  int64 size_bytes = 5;
  string checksum = 6;            // For integrity verification
  map<string, string> metadata = 7;
}
```

## Service Architecture

### 1. Engine Service (Routing Helper)
**Purpose**: DNS-like node resolution and cross-cluster routing
**Key Features**:
- Node discovery and resolution
- Cross-cluster communication
- Kafka topic subscription management
- Health monitoring and metrics

```protobuf
service EngineService {
  rpc ProcessAtNode(ProcessAtNodeRequest) returns (ProcessAtNodeResponse);
  rpc ProcessStream(stream ProcessAtNodeRequest) returns (stream ProcessAtNodeResponse);
  rpc RouteToCluster(RouteToClusterRequest) returns (RouteToClusterResponse);
  rpc UpdateTopicSubscriptions(UpdateTopicSubscriptionsRequest) returns (UpdateTopicSubscriptionsResponse);
  rpc GetTopicSubscriptions(GetTopicSubscriptionsRequest) returns (GetTopicSubscriptionsResponse);
  rpc GetHealth(HealthRequest) returns (HealthResponse);
}
```

### 2. Module Service (Processing Nodes)
**Purpose**: Clean processing interface for all pipeline modules
**Key Features**:
- Standard gRPC health checks
- Dual transport support (Kafka + gRPC)
- Configuration schema exposure

```protobuf
service ModuleService {
  rpc ProcessDocument(ProcessDocumentRequest) returns (ProcessDocumentResponse);
  rpc ProcessStream(stream ProcessDocumentRequest) returns (stream ProcessDocumentResponse);
  rpc GetConfigSchema(GetConfigSchemaRequest) returns (GetConfigSchemaResponse);
  rpc ValidateConfig(ValidateConfigRequest) returns (ValidateConfigResponse);
}
```

### 3. Repository Service (Redis-Based Storage)
**Purpose**: Document storage with DNS-like node resolution using Redis
**Key Features**:
- CRUD operations for PipeDoc using Redis
- Node-aware storage (documents tagged with processing nodes)
- Graph structure storage (no loops allowed, tree-like persistence)
- Cluster-specific virtual drives (each cluster gets its own logical partition within Redis)
- Single Redis instance per application platform

```protobuf
service RepositoryService {
  rpc StorePipeDoc(StorePipeDocRequest) returns (StorePipeDocResponse);
  rpc GetPipeDoc(GetPipeDocRequest) returns (GetPipeDocResponse);
  rpc QueryPipeDocs(QueryPipeDocsRequest) returns (QueryPipeDocsResponse);
  rpc DeletePipeDoc(DeletePipeDocRequest) returns (DeletePipeDocResponse);
  rpc GetNodeDocuments(GetNodeDocumentsRequest) returns (GetNodeDocumentsResponse);
}
```

### 4. Design Mode Service (Frontend Simulation)
**Purpose**: Testing pipelines before deployment
**Key Features**:
- Simulate processing without real Kafka topics
- Frontend integration for pipeline design
- Configuration validation

```protobuf
service DesignModeService {
  rpc CreateSimulationSession(CreateSimulationSessionRequest) returns (CreateSimulationSessionResponse);
  rpc SimulateProcessing(SimulateProcessingRequest) returns (SimulateProcessingResponse);
  rpc GetSimulationResults(GetSimulationResultsRequest) returns (GetSimulationResultsResponse);
  rpc ValidatePipelineConfig(ValidatePipelineConfigRequest) returns (ValidatePipelineConfigResponse);
}
```

### 5. Configuration Service (Pipeline Management)
**Purpose**: Pipeline and cluster configuration management
**Key Features**:
- Pipeline graph definitions
- Module whitelisting
- Cluster configuration

```protobuf
service PipelineConfigService {
  rpc CreatePipelineGraph(CreatePipelineGraphRequest) returns (CreatePipelineGraphResponse);
  rpc GetPipelineGraph(GetPipelineGraphRequest) returns (GetPipelineGraphResponse);
  rpc UpdatePipelineGraph(UpdatePipelineGraphRequest) returns (UpdatePipelineGraphResponse);
  rpc WhitelistModule(WhitelistModuleRequest) returns (WhitelistModuleResponse);
  rpc GetClusterConfig(GetClusterConfigRequest) returns (GetClusterConfigResponse);
}
```

## Kafka Integration Strategy

### Topic Naming Convention
- Follow same pattern as node IDs: `{cluster_id}.{node_name}.{suffix}`
- Examples:
  - `prod.chunker-v1.input` - Input topic for chunker
  - `prod.chunker-v1.output` - Output topic from chunker
  - `prod.chunker-v1.dlq` - Dead letter queue

### Subscription Management
- Engine manages topic subscriptions dynamically
- Nodes can request subscription changes via Engine
- Load balancing across multiple engine instances

### Message Format
- All Kafka messages contain PipeStream + PipeDoc
- Consistent serialization (protobuf)
- Partition by doc_id for ordering guarantees

## Migration Strategy

### Phase 1: Skeleton Services (Week 1)
1. **Create new grpc-stubs project** with ONLY new protobuf definitions (no v2 suffix)
2. **Implement skeleton services**:
   - Engine service with basic routing
   - Module service interface
   - Repository service with Redis
   - Design mode service for frontend
   - Configuration service for pipeline management
3. **Basic health checks** and service registration
4. **Docker containers** for each service

### Phase 2: Core Functionality (Week 2)
1. **Engine routing logic**:
   - Node discovery via Consul
   - Basic gRPC routing between nodes
   - Kafka topic management
2. **Module interface implementation**:
   - Refactor existing modules (parser, chunker, embedder) to new interface
   - Maintain existing functionality, new interface
3. **Repository integration**:
   - Redis storage for PipeDoc
   - Graph structure persistence (tree-like, no loops)
   - Cluster-specific virtual drives within single Redis instance

### Phase 3: Transport Layer (Week 3)
1. **Kafka integration**:
   - Topic creation and management
   - Message serialization/deserialization
   - Consumer group management
2. **Dual transport support**:
   - Nodes can communicate via Kafka OR gRPC
   - Engine routes based on configuration
3. **Cross-cluster communication**:
   - Engine-to-engine communication
   - Remote node resolution

### Phase 4: Frontend Integration (Week 4)
1. **Design mode implementation**:
   - Simulation without real Kafka topics
   - Frontend API for pipeline design
2. **Configuration management**:
   - Pipeline graph CRUD operations
   - Module whitelisting
   - Cluster configuration
3. **Existing frontend adaptation**:
   - Update existing Vue.js components
   - New APIs for graph-based design

### Phase 5: Production Features (Week 5)
1. **Monitoring and observability**:
   - Distributed tracing
   - Metrics collection
   - Health monitoring
2. **Error handling**:
   - Dead letter queues
   - Retry policies
   - Circuit breakers
3. **Performance optimization**:
   - Connection pooling
   - Caching strategies
   - Load balancing

## Components to Retain

### Keep and Refactor
- **dynamic-grpc**: Still useful for service discovery and dynamic gRPC calls
- **consul-client**: Service registration and discovery
- **filesystem utilities**: File operations and crawling
- **Vue.js frontend**: Update to use new APIs
- **Redis integration**: Adapt for new PipeDoc structure and graph storage
- **Docker infrastructure**: Containers and orchestration
- **Testing frameworks**: Adapt test cases for new interfaces

### Components to Remove/Replace
- **Current pipeline orchestration**: Replace with DNS-like routing
- **V1 protobuf definitions**: Replace with clean new definitions
- **Linear pipeline thinking**: Replace with graph-based processing
- **Central orchestrator bottleneck**: Distribute processing logic
- **Complex configuration hierarchy**: Simplify with graph-based config

## Implementation Approach

### Top-Down Strategy
1. **Start with service skeletons**: Get all gRPC services running with stub implementations
2. **Implement core data flow**: PipeStream + PipeDoc through the system
3. **Add transport layers**: Kafka and gRPC communication
4. **Integrate existing components**: Bring over useful libraries and utilities
5. **Frontend adaptation**: Update UI to work with new APIs
6. **Production hardening**: Add monitoring, error handling, performance optimization

### Breaking Changes Acceptance
- **Full API redesign**: New protobuf definitions, new service interfaces
- **Configuration format changes**: New pipeline graph format
- **Module interface changes**: All modules need refactoring
- **Storage changes**: New PipeDoc structure in Redis with graph-based organization
- **Frontend API changes**: New REST/gRPC APIs for UI

## Key Benefits

### Scalability
- No central orchestrator bottleneck
- Nodes can scale independently
- Kafka provides natural load balancing

### Flexibility
- Nodes can be added/removed dynamically
- Multiple processing paths through the graph
- Easy A/B testing with different node versions

### Reliability
- No single point of failure
- Kafka provides message persistence
- Nodes can fail without affecting others

### Observability
- Distributed tracing through PipeStream
- Hop count and processing history
- Clear audit trail for debugging

## Success Metrics

### Technical Metrics
- **Throughput**: 10x improvement over current linear model
- **Latency**: Sub-100ms for gRPC paths, sub-1s for Kafka paths
- **Availability**: 99.9% uptime with no single point of failure
- **Scalability**: Linear scaling with node additions

### Development Metrics
- **Time to add new module**: < 1 day (vs current 1 week)
- **Pipeline configuration time**: < 1 hour (vs current 1 day)
- **Testing cycle time**: < 30 minutes (vs current 2 hours)
- **Deployment time**: < 15 minutes (vs current 1 hour)

## Risk Mitigation

### Technical Risks
- **Complexity**: Start simple, add features incrementally
- **Performance**: Benchmark early and often
- **Data consistency**: Use Kafka for ordering guarantees
- **Service discovery**: Leverage proven Consul infrastructure

### Business Risks
- **Migration time**: Phased approach with parallel systems
- **Training**: Comprehensive documentation and examples
- **Rollback plan**: Keep current system running during migration
- **Testing**: Extensive integration testing before production

## Conclusion

This new architecture represents a fundamental shift from linear pipeline orchestration to a distributed graph network. The DNS-like node resolution model eliminates bottlenecks while maintaining simplicity. The clean separation of concerns (PipeStream for routing, PipeDoc for data) provides a solid foundation for scalable, reliable document processing.

The implementation strategy focuses on getting skeleton services running first, then building up functionality incrementally. This top-down approach ensures we have a working system at each stage while allowing for breaking changes and complete refactoring of existing components.

The expected benefits include 10x throughput improvements, sub-second latency, and dramatically reduced development time for new features. The risk mitigation strategy ensures a smooth transition with minimal business disruption.