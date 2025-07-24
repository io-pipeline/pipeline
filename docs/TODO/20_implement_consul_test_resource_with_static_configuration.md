## 20. Implement ConsulTestResource with Static Configuration

**Goal:** Refactor the `ConsulTestResource` to use static configuration access instead of CDI, ensuring it can run before the CDI container is initialized.

### Background
The `ConsulTestResource` is a `QuarkusTestResourceLifecycleManager` that runs before the CDI container is initialized. This means it cannot use `@Inject` or `@ConfigProperty` to access configuration. The current implementation attempts to inject `TestConsulConfig`, which fails because the CDI container is not yet available.

### Ticket 20.1: Refactor `ConsulTestResource` to Use Static Configuration

**Title:** Refactor: Use `ConfigProvider` to access configuration in `ConsulTestResource`

**Description:**
Modify the `ConsulTestResource` to access configuration statically using `ConfigProvider.getConfig()`. This will allow it to retrieve the necessary configuration values without relying on CDI injection.

**Tasks:**
1.  Remove the `@Inject` annotation and the `TestConsulConfig` field from `ConsulTestResource`.
2.  In the `start()` method, use `ConfigProvider.getConfig().getValue(...)` to read the `quarkus.consul.port` and other required configuration properties.
3.  Ensure that the `ConsulTestResource` can still correctly start and configure the Consul test container.

### Ticket 20.2: Update `TestConsulConfig` for Clarity

**Title:** Refactor: Add comments to `TestConsulConfig` to clarify its usage

**Description:**
Add comments to the `TestConsulConfig` class to explain that it is intended for use in tests where CDI is available, and that `ConsulTestResource` uses a different mechanism for configuration.

**Tasks:**
1.  Add a class-level Javadoc comment to `TestConsulConfig` explaining its purpose and limitations.
2.  Add comments to the fields to clarify where they are used.
