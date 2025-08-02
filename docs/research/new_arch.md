# V2 Graph-First Architecture Implementation Plan

## Overview
The V2 architecture transforms the pipeline from a linear orchestrator to a **DNS-like graph network** where nodes exist independently and can be discovered/traversed without full graph awareness. This enables massive scalability and cross-datacenter processing.

## Core Architectural Changes

### From Linear Pipeline → Graph Network
- **V1**: Engine orchestrates full pipeline with complex validation
- **V2**: Engine is lightweight router, nodes exist independently
- **Benefit**: Scales to millions of nodes across multiple datacenters

### From Complex Config → Simple Node Resolution
- **V1**: Pipeline configs with complex step dependencies
- **V2**: DNS-like node lookup: `nodeId` → `NodeLookupResponse` → route
- **Benefit**: O(1) routing, no full graph traversal needed

### From Compile-time Dependencies → Runtime Discovery
- **V1**: Hardcoded gRPC service names in config
- **V2**: Dynamic service discovery via existing `dynamic-grpc` library
- **Benefit**: Add/remove modules without engine restarts

## Implementation Approach

### Phase 1: Module V2 Interface (Start Here)
**Goal**: Add V2 interface to existing modules without breaking V1

#### Step 1.1: Generate V2 Protobuf Classes
```bash
# Generate Java classes from new V2 protos
./gradlew generateProto
```

#### Step 1.2: Implement Dual Interface Pattern
**Target Modules**: chunker, parser, embedder, echo, test-harness

```java
@GrpcService
public class ChunkerModuleV2 implements PipeStepProcessor, ModuleV2Service {
    
    // Keep existing V1 interface
    @Override
    public Uni<ProcessResponse> processData(ProcessRequest request) {
        // Existing implementation
    }
    
    // Add new V2 interface
    @Override
    public Uni<ProcessDocumentResponse> processDocument(ProcessDocumentRequest request) {
        // Convert V2 → V1, process, convert V1 → V2
        PipeDoc v2Doc = request.getDocument();
        ProcessRequest v1Request = convertToV1(v2Doc, request.getConfig());
        
        return processData(v1Request)
            .map(v1Response -> convertToV2(v1Response));
    }
    
    @Override
    public Uni<ModuleInfoResponse> getModuleInfo(ModuleInfoRequest request) {
        return Uni.createFrom().item(ModuleInfoResponse.newBuilder()
            .setModuleId("chunker")
            .setModuleName("Document Chunker")
            .setVersion("2.0.0")
            .addSupportedInputTypes("text/plain")
            .addSupportedOutputTypes("application/json")
            .setCapabilities(ModuleCapabilities.newBuilder()
                .setSupportsStreaming(true)
                .setSupportsBinaryData(false)
                .setSupportsStructuredData(true)
                .setMaxDocumentSizeBytes(10_000_000)
                .build())
            .setHealth(ModuleHealth.MODULE_HEALTH_HEALTHY)
            .build());
    }
}
```

#### Step 1.3: Add V2 Registration
```java
@ApplicationScoped
public class ModuleRegistrationV2Client {
    
    @Inject
    DynamicGrpcClientFactory grpcClientFactory;
    
    public void registerV2Capabilities() {
        var registrationClient = grpcClientFactory
            .getMutinyClientForService("module-registration-v2");
            
        registrationClient.registerModuleV2(RegisterModuleV2Request.newBuilder()
            .setModuleId("chunker")
            .setHost("localhost")
            .setPort(39102)
            .setSupportsV2Interface(true)
            .addSupportedNodeTypes("PROCESSOR")
            .setConfigSchemaJson(getConfigSchemaJson())
            .build());
    }
}
```

### Phase 2: Module Registration Service V2
**Goal**: Enhance registration service to track V2 capabilities

#### Step 2.1: Implement Registration V2 Service
```java
@GrpcService
public class ModuleRegistrationV2ServiceImpl implements ModuleRegistrationV2Service {
    
    @Inject
    ModuleRepository moduleRepository;
    
    @Override
    public Uni<RegisterModuleV2Response> registerModuleV2(RegisterModuleV2Request request) {
        // Store enhanced module metadata
        ModuleDefinition definition = ModuleDefinition.newBuilder()
            .setModuleId(request.getModuleId())
            .setImplementationName(request.getModuleDefinition().getImplementationName())
            .setGrpcServiceName(request.getModuleDefinition().getGrpcServiceName())
            .build();
            
        return moduleRepository.saveModuleDefinition(definition)
            .map(saved -> RegisterModuleV2Response.newBuilder()
                .setSuccess(true)
                .setAssignedModuleId(saved.getModuleId())
                .build());
    }
    
    @Override
    public Uni<ListGraphCapableModulesResponse> listGraphCapableModules(
            ListGraphCapableModulesRequest request) {
        return moduleRepository.findGraphCapableModules(request.getClusterId())
            .map(modules -> ListGraphCapableModulesResponse.newBuilder()
                .addAllModules(modules)
                .build());
    }
}
```

### Phase 3: Frontend Graph Builder
**Goal**: Create UI for building graphs with V2 modules

#### Step 3.1: Module Discovery API
```typescript
// Frontend service to discover V2 modules
export class ModuleDiscoveryService {
    async getGraphCapableModules(clusterId?: string): Promise<GraphCapableModule[]> {
        const response = await this.grpcClient.listGraphCapableModules({
            clusterId: clusterId
        });
        return response.modules;
    }
    
    async getModuleConfigSchema(moduleId: string): Promise<JSONSchema7> {
        const response = await this.grpcClient.getModuleForGraph({
            moduleId: moduleId
        });
        return JSON.parse(response.configSchemaJson);
    }
}
```

#### Step 3.2: Graph Builder Component
```vue
<template>
  <div class="graph-builder">
    <!-- Module Palette -->
    <div class="module-palette">
      <h3>Available Modules</h3>
      <div v-for="module in availableModules" :key="module.moduleId"
           class="module-card"
           draggable="true"
           @dragstart="startDrag(module)">
        <h4>{{ module.implementationName }}</h4>
        <p>{{ module.supportedNodeTypes.join(', ') }}</p>
      </div>
    </div>
    
    <!-- Graph Canvas -->
    <div class="graph-canvas" 
         @drop="dropModule" 
         @dragover.prevent>
      <GraphNode v-for="node in graphNodes" 
                 :key="node.nodeId"
                 :node="node"
                 @connect="createEdge" />
    </div>
    
    <!-- Properties Panel -->
    <div class="properties-panel">
      <h3>Node Configuration</h3>
      <JsonSchemaForm v-if="selectedNode"
                      :schema="selectedNodeSchema"
                      :model="selectedNode.customConfig"
                      @update="updateNodeConfig" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ModuleDiscoveryService } from './services/ModuleDiscoveryService';

const moduleService = new ModuleDiscoveryService();
const availableModules = ref<GraphCapableModule[]>([]);
const graphNodes = ref<GraphNode[]>([]);
const selectedNode = ref<GraphNode | null>(null);

onMounted(async () => {
  availableModules.value = await moduleService.getGraphCapableModules();
});

function dropModule(event: DragEvent) {
  const moduleData = JSON.parse(event.dataTransfer?.getData('text/plain') || '{}');
  const newNode: GraphNode = {
    nodeId: generateNodeId(),
    clusterId: 'default',
    name: moduleData.implementationName,
    nodeType: moduleData.supportedNodeTypes[0],
    moduleId: moduleData.moduleId,
    customConfig: {},
    transport: getDefaultTransport(),
    visibility: 'CLUSTER_VISIBILITY_PUBLIC',
    createdAt: Date.now(),
    modifiedAt: Date.now()
  };
  
  graphNodes.value.push(newNode);
}
</script>
```

### Phase 4: Engine V2 Implementation
**Goal**: Lightweight engine that routes between nodes

#### Step 4.1: DNS-like Node Resolution
```java
@ApplicationScoped
public class NodeResolver {
    
    @Inject
    @CacheName("node-cache")
    Cache cache;
    
    @Inject
    RepositoryV2Service repositoryService;
    
    public Uni<NodeLookupResponse> resolveNode(String nodeId, String clusterId) {
        // Try Redis cache first
        NodeLookupResponse cached = cache.get(nodeId, NodeLookupResponse.class);
        if (cached != null) {
            return Uni.createFrom().item(cached.toBuilder()
                .setCacheHit(true)
                .build());
        }
        
        // Fallback to repository
        return repositoryService.resolveNode(ResolveNodeRequest.newBuilder()
            .setNodeId(nodeId)
            .setClusterId(clusterId)
            .setUseCache(false)
            .build())
            .invoke(response -> cache.put(nodeId, response));
    }
}
```

#### Step 4.2: Engine V2 Service Implementation
```java
@GrpcService
public class EngineV2ServiceImpl implements EngineV2Service {
    
    @Inject
    NodeResolver nodeResolver;
    
    @Inject
    DynamicGrpcClientFactory grpcClientFactory;
    
    @Override
    public Uni<ProcessAtNodeResponse> processAtNode(ProcessAtNodeRequest request) {
        PipeStream stream = request.getStream();
        String targetNodeId = stream.getTargetNodeId();
        
        return nodeResolver.resolveNode(targetNodeId, stream.getClusterId())
            .flatMap(nodeResponse -> {
                // Call module via dynamic-grpc
                String serviceName = nodeResponse.getModule().getGrpcServiceName();
                
                return grpcClientFactory.getMutinyClientForService(serviceName)
                    .flatMap(client -> {
                        ProcessDocumentRequest docRequest = ProcessDocumentRequest.newBuilder()
                            .setDocument(stream.getDocument())
                            .setTraceId(stream.getTraceId())
                            .build();
                            
                        return client.processDocument(docRequest);
                    })
                    .map(result -> {
                        // Update stream and find next nodes
                        PipeStream updatedStream = stream.toBuilder()
                            .setCurrentNodeId(targetNodeId)
                            .setHopCount(stream.getHopCount() + 1)
                            .addProcessingPath(targetNodeId)
                            .setDocument(result.getProcessedDocument())
                            .setLastProcessedAt(Timestamp.newBuilder()
                                .setSeconds(System.currentTimeMillis() / 1000)
                                .build())
                            .build();
                            
                        return ProcessAtNodeResponse.newBuilder()
                            .setSuccess(result.getSuccess())
                            .setMessage(result.getMessage())
                            .setUpdatedStream(updatedStream)
                            .addAllNextNodes(findNextNodes(nodeResponse.getOutgoingEdges()))
                            .build();
                    });
            });
    }
}
```

## Migration Timeline

### Week 1: Foundation
- [ ] Generate V2 protobuf classes
- [ ] Implement dual interface in chunker module
- [ ] Test V2 interface with simple document

### Week 2: Module Coverage
- [ ] Add V2 interface to parser, embedder, echo modules
- [ ] Implement module registration V2 service
- [ ] Test module discovery and registration

### Week 3: Frontend Integration
- [ ] Create module discovery service
- [ ] Build basic graph builder UI
- [ ] Implement drag-and-drop node creation

### Week 4: Engine Implementation
- [ ] Implement node resolver with Redis cache
- [ ] Build engine V2 service
- [ ] Test end-to-end document processing

### Week 5: Demo Preparation
- [ ] Create sample graphs
- [ ] Add monitoring and metrics
- [ ] Performance testing and optimization

## Key Benefits Achieved

### Scalability
- **Millions of nodes** supported via Redis cache
- **Cross-datacenter** processing with cluster awareness
- **Independent scaling** of engine instances

### Simplicity
- **DNS-like resolution** - no complex pipeline orchestration
- **Lightweight engine** - just routing logic
- **Module independence** - add/remove without system changes

### Developer Experience
- **Visual graph building** - drag-and-drop interface
- **Real-time collaboration** - multiple users editing same graph
- **Schema-driven forms** - automatic UI generation from module schemas

### Operational Excellence
- **Zero-downtime deployments** - modules register/unregister dynamically
- **Fault isolation** - one module failure doesn't affect others
- **Observability** - distributed tracing across all hops

## Success Metrics
- [ ] Process 10,000 documents through 5-node graph in <30 seconds
- [ ] Support 100 concurrent graph editing sessions
- [ ] Deploy new module without engine restart
- [ ] Cross-cluster document processing working
- [ ] Frontend graph builder fully functional

This architecture transforms your pipeline into a **scalable, distributed processing network** while maintaining backward compatibility and leveraging your existing infrastructure investments.