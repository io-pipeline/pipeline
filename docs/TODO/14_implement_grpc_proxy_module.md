## 14. Implement the gRPC Proxy Module

**Goal:** Implement the `proxy-module` to act as an intelligent wrapper around any gRPC service, enabling the application of Quarkus-backed features like transformation, validation, and caching without modifying the target service.

### Ticket 14.1: Implement Core Request Forwarding Logic

**Title:** Feature: Implement the basic gRPC request forwarding in the proxy module

**Description:**
The fundamental feature of the proxy is to receive a gRPC call and forward it to a configured upstream gRPC service.

**Tasks:**
1.  The proxy module will expose a generic gRPC service interface that can accept arbitrary requests.
2.  Implement the logic to dynamically look up the target service's address (e.g., from Consul or from configuration passed in the request).
3.  Use the `DynamicGrpcClientFactory` to create a client for the target service.
4.  Forward the incoming request to the target service and stream the response back to the original caller.

### Ticket 14.2: Integrate Engine Libraries for Interception

**Title:** Feature: Add interception points to the proxy for applying cross-cutting concerns

**Description:**
The real power of the proxy comes from its ability to intercept requests and apply common pipeline features.

**Tasks:**
1.  Integrate the `ProtoFieldMapper` into the proxy. Allow the proxy to be configured with pre-request and post-response mapping rules.
2.  Integrate the `validators` library. Allow the proxy to be configured to run a set of validations against the request or response messages.
3.  Add a caching layer. Allow the proxy to be configured to cache responses for specific methods based on the request content.

### Ticket 14.3: Develop a Dynamic Configuration Model

**Title:** Feature: Design and implement a dynamic configuration system for the proxy

**Description:**
The proxy's behavior for each upstream service needs to be configurable at runtime without redeployment.

**Tasks:**
1.  Design a configuration schema (e.g., a YAML or JSON format) that defines a proxy target. This schema will specify:
    *   The upstream service name.
    *   The list of pre- and post-processing mapping rules (`ProtoFieldMapper`).
    *   The list of validations to apply.
    *   Caching policies (e.g., TTL, cache key).
2.  Implement a mechanism for the proxy to load this configuration dynamically. This could be from a designated key in the Consul KV store.
3.  When a pipeline step is configured to use the proxy, it will pass a reference to the proxy's configuration, allowing the proxy to look up the correct set of rules to apply.
