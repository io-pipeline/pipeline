## 21. Implement Dynamic gRPC Client Management

**Goal:** Refactor the `DynamicGrpcClientFactory` to improve its robustness and flexibility by implementing a cache for gRPC channels and clients, and by adding support for custom interceptors.

### Ticket 21.1: Implement a Cache for gRPC Channels and Clients

**Title:** Feature: Add a cache for gRPC channels and clients in `DynamicGrpcClientFactory`

**Description:**
The `DynamicGrpcClientFactory` currently creates a new channel and client for every request. This is inefficient and can lead to resource exhaustion. A cache should be implemented to reuse channels and clients for the same service.

**Tasks:**
1.  Implement a cache (e.g., using `Caffeine`) to store `ManagedChannel` and `MutinyStub` instances.
2.  The cache key should be based on the service name and address.
3.  When a client is requested, the factory should first check the cache. If a client is found, it should be returned. Otherwise, a new client should be created and added to the cache.
4.  Implement a mechanism to evict idle clients from the cache to prevent resource leaks.

### Ticket 21.2: Add Support for Custom Interceptors

**Title:** Feature: Allow custom interceptors to be added to dynamically created gRPC clients

**Description:**
The `DynamicGrpcClientFactory` should allow custom `ClientInterceptor` instances to be added to the clients it creates. This will enable cross-cutting concerns like logging, metrics, and tracing to be applied to all dynamic gRPC calls.

**Tasks:**
1.  Modify the `getMutinyClient` methods in `DynamicGrpcClientFactory` to accept an optional list of `ClientInterceptor` instances.
2.  When creating a new channel, apply the provided interceptors using `ManagedChannelBuilder.intercept()`.
3.  Update the `ProcessingBufferInterceptor` to use this new mechanism to apply itself to dynamic gRPC calls.
