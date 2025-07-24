## 8. Fix Hostname Resolution and Service Discovery

**Goal:** Ensure that all gRPC communication uses Consul for service discovery to avoid `UnknownHostException` errors and make the system more robust in containerized environments.

### Ticket 8.1: Use Dynamic Client Factory for Registration Health Checks

**Title:** Refactor: Use `DynamicGrpcClientFactory` for health checks during service registration

**Description:**
The `registration-service` currently attempts to connect back to a registering module using `ManagedChannelBuilder.forAddress()`, which can fail in containerized environments. It should instead use the existing `DynamicGrpcClientFactory` to perform the health check. The factory will correctly use the provided host and port to establish a connection without relying on DNS for resolution in a way that's consistent with the rest of the system.

**Tasks:**
1.  In `ModuleRegistrationService.java`, inject the `DynamicGrpcClientFactory`.
2.  Instead of creating a `ManagedChannel` manually, use the factory's `getMutinyClient(host, port)` method to get a client for the registering module.
3.  This ensures that channel creation and management are handled consistently by the factory.

### Ticket 8.2: Audit and Refactor Manual Channel Creation

**Title:** Refactor: Audit manual gRPC channel creation and use appropriate discovery mechanisms

**Description:**
There are several places in the codebase, particularly in tests, where `ManagedChannelBuilder.forAddress(...)` is used directly. While sometimes necessary, these should be reviewed. Where possible, they should be replaced by either the `DynamicGrpcClientFactory` (for dynamic service names) or a Stork-powered `@GrpcClient` (for services with names known at compile time).

**Tasks:**
1.  Review all usages of `ManagedChannelBuilder.forAddress(...)`.
2.  If the service name is *dynamic* (e.g., in `ModuleRegistrationService`), use `DynamicGrpcClientFactory`.
3.  If the service name is *static* and known at compile time (e.g., a core service that's always present), refactor to use `@GrpcClient` with Stork.
4.  For test code, evaluate if using Testcontainers with a proper test profile that configures Stork is a cleaner approach than direct channel creation.

### Ticket 8.3: Ensure All Services Register with a Reachable Address

**Title:** Chore: Ensure all services register with a reachable IP address or hostname

**Description:**
Services should not register with Consul using their container hostname, as this is often not resolvable by other services. Instead, they should register with an IP address or a hostname that is reachable from other containers. The `PipelineAutoRegistrationBean` already has logic to determine the correct host, but this needs to be verified and applied consistently.

**Tasks:**
1.  Review the `determineHost()` method in `PipelineAutoRegistrationBean.java` and ensure it correctly identifies the reachable IP address in all deployment scenarios (local, Docker Compose, Kubernetes).
2.  Verify that all services that use `@PipelineAutoRegister` or `@ConsulAutoRegister` are correctly configured to provide a reachable address.
3.  Update the `CONFIGURATION.md` document to include clear instructions on how to configure the registration host for different environments.
