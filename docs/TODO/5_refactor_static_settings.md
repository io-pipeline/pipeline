## 5. Refactor Static Settings

**Goal:** Remove the use of static state to improve testability, prevent memory leaks, and make the application more robust.

### Ticket 5.1: Refactor Static `bufferCache` in `ProcessingBufferInterceptor`

**Title:** Refactor: Replace static `bufferCache` with a CDI bean

**Description:**
The `ProcessingBufferInterceptor` currently uses a `static ConcurrentHashMap` to cache `ProcessingBuffer` instances. This can lead to state leakage between tests and potential memory leaks. This should be refactored to use a CDI-managed bean for the cache.

**Tasks:**
1.  Create a new `@ApplicationScoped` bean called `ProcessingBufferCache`.
2.  Move the `bufferCache` map from `ProcessingBufferInterceptor` to the new `ProcessingBufferCache` bean.
3.  Inject the `ProcessingBufferCache` into the `ProcessingBufferInterceptor`.
4.  Update the `getOrCreateBuffer` method in the interceptor to use the injected cache.
5.  Refactor the `static` buffer access methods (`getBuffer`, `saveAllBuffers`, etc.) into a new `@ApplicationScoped` bean called `ProcessingBufferManager`.
6.  Inject the `ProcessingBufferCache` into the `ProcessingBufferManager` to provide access to the buffers.
7.  Update any code that was using the static methods to instead inject and use the `ProcessingBufferManager`.
8.  Ensure that all tests that rely on the buffer functionality are updated to use the new manager bean and that they still pass.

### Ticket 5.2: Refactor `SimpleConsulClientFactory` to be a CDI Bean

**Title:** Refactor: Convert `SimpleConsulClientFactory` to a CDI-managed bean

**Description:**
The `SimpleConsulClientFactory` currently uses static fields to manage a shared `ConsulClient` and `Vertx` instance across tests. This is not ideal as it can lead to state leakage and resource management issues. It should be refactored into a proper CDI bean to allow Quarkus to manage its lifecycle.

**Tasks:**
1.  Convert `SimpleConsulClientFactory` into an `@ApplicationScoped` CDI bean.
2.  Remove the `static` modifiers from the `testClient` and `vertx` fields.
3.  Use `@Inject` to get a `Vertx` instance instead of creating it manually.
4.  Change the `getTestClient()` method to be a non-static producer method (`@Produces`) that returns the `ConsulClient`.
5.  The producer method should be `@ApplicationScoped` to ensure only one instance of the `ConsulClient` is created for the application's lifecycle.
6.  Remove the `cleanup()` method and rely on Quarkus's lifecycle management to close the Vert.x and Consul clients.
7.  Update all tests that use `SimpleConsulClientFactory.getTestClient()` to instead inject the `ConsulClient` directly.

### Ticket 5.3: Refactor Static Test Data Helper in `EchoServiceRealDataTest`

**Title:** Refactor: Remove static `ProtobufTestDataHelper` from `EchoServiceRealDataTest`

**Description:**
The `EchoServiceRealDataTest` uses a static `ProtobufTestDataHelper` which can lead to test flakiness and state leakage. The test already injects a non-static instance of the helper, so the static field is redundant and should be removed.

**Tasks:**
1.  Remove the `staticHelper` field from `EchoServiceRealDataTest`.
2.  Remove the `@BeforeAll` method that initializes the `staticHelper`.
3.  Update the `sampleDocuments()` method to be non-static and use the injected `testDataHelper` field instead of the `staticHelper`.
4.  Update the `@MethodSource` annotation on the `testProcessSampleDocuments` test to call the non-static `sampleDocuments()` method.
5.  Verify that the tests in `EchoServiceRealDataTest` still pass after the refactoring.
