## 9. Standardize on Reactive gRPC Stubs

**Goal:** Ensure that all gRPC communication in the `dynamic-grpc` module uses reactive, Mutiny-based stubs to maintain the benefits of a reactive architecture.

### Ticket 9.1: Deprecate and Remove Blocking gRPC Client Methods

**Title:** Refactor: Remove blocking gRPC client methods from `DynamicGrpcClientFactory`

**Description:**
The `DynamicGrpcClientFactory` currently provides both blocking and reactive (Mutiny) gRPC clients. To prevent accidental use of blocking clients in a reactive application, the blocking methods should be removed.

**Tasks:**
1.  Mark the `getClient(...)` and `getClientForService(...)` methods in `DynamicGrpcClientFactory` as `@Deprecated`.
2.  Find all usages of these methods in the codebase and replace them with calls to the corresponding `getMutinyClient(...)` or `getMutinyClientForService(...)` methods.
3.  Once all usages have been replaced, remove the deprecated methods from `DynamicGrpcClientFactory`.
4.  Ensure that all tests still pass after the refactoring.
