r# Project Cleanup Plan

## 1. Gradle Consolidation

**Goal:** Centralize Gradle configuration to simplify maintenance and ensure consistency across all modules.

### Ticket 1.1: Consolidate Repository Definitions

**Title:** Refactor: Centralize Maven repository definitions in root Gradle files

**Description:**
Currently, Maven repositories (`mavenCentral()`, `mavenLocal()`) are declared in the root `build.gradle` as well as in multiple subproject `build.gradle` files. This is redundant and can lead to inconsistencies.

**Tasks:**
1.  Remove the `repositories` blocks from all subproject `build.gradle` files.
2.  Ensure that the `allprojects` block in the root `build.gradle` is the single source of truth for project repositories.
3.  Verify that the `pluginManagement` block in `settings.gradle` correctly defines the repositories for Gradle plugins.
4.  Confirm that the project still builds and resolves dependencies correctly after the changes.

### Ticket 1.2: Centralize Proxy Settings

**Title:** Feature: Add centralized proxy configuration for Gradle

**Description:**
There is no project-wide mechanism for configuring proxy settings for downloading dependencies and plugins. This should be added to the root `gradle.properties` file to allow developers to easily configure it for their environment.

**Tasks:**
1.  Add standard Gradle properties to the root `gradle.properties` file as commented-out examples.
    ```properties
    #systemProp.http.proxyHost=your.proxy.host
    #systemProp.http.proxyPort=8080
    #systemProp.http.proxyUser=your_user
    #systemProp.http.proxyPassword=your_password
    #systemProp.https.proxyHost=your.proxy.host
    #systemProp.https.proxyPort=8080
    #systemProp.https.proxyUser=your_user
    #systemProp.https.proxyPassword=your_password
    ```
2.  Document in `README.md` or a new `CONTRIBUTING.md` file how to configure the proxy for the project.

### Ticket 1.3: Standardize Plugin Application

**Title:** Refactor: Standardize Gradle plugin application using version catalog aliases

**Description:**
Some modules apply plugins using `id 'plugin.name'` while the root project uses the safer `alias(libs.plugins.pluginName)` syntax. All plugin applications should be standardized to use the version catalog aliases defined in `gradle/libs.versions.toml`.

**Tasks:**
1.  Review all `build.gradle` files in subprojects.
2.  For each plugin applied with `id '...'`, ensure a corresponding alias exists in `gradle/libs.versions.toml`.
3.  Update the `plugins` block in each subproject to use the `alias(...)` syntax.
4.  Verify that the project builds correctly after the changes.

## 2. Implement Fan-Out with Kafka

**Goal:** Introduce an asynchronous, message-based communication channel using Kafka to enable fan-out scenarios and improve pipeline scalability and resilience.

### Ticket 2.1: Implement Dynamic Kafka Listeners

**Title:** Feature: Implement dynamic Kafka topic listeners

**Description:**
To support dynamic pipelines where topics can be created, destroyed, and repartitioned at runtime, a dynamic Kafka listener management system is required. This system will replace the need for static `@Incoming` annotations and allow the application to adapt to changing topic configurations.

**Tasks:**
1.  Design and implement a service that can start and stop Kafka consumers on demand.
2.  This service should be able to monitor for new topics (perhaps via a naming convention or a configuration source) and automatically create consumers for them.
3.  The service should also handle rebalancing and gracefully shut down consumers for topics that are no longer needed.
4.  Integrate this dynamic listener service with the `pipestream-engine` to manage the lifecycle of consumers based on the deployed pipeline configurations.

### Ticket 2.2: Update Pipestream Engine to Support Kafka Outputs

**Title:** Feature: Add Kafka producer support to the Pipestream Engine

**Description:**
The `PipeStreamEngineImpl` needs to be updated to handle steps that output to a Kafka topic instead of (or in addition to) a gRPC service.

**Tasks:**
1.  In `PipeStreamEngineImpl`, when a step's output is of type `KAFKA`, use a Kafka producer to send the data to the specified topic.
2.  The engine should be able to handle multiple output types for a single step (e.g., one gRPC output and one Kafka output). This will likely require changes to the `PipelineStepConfig` model to allow a list of outputs.
3.  Ensure that the engine can gracefully handle Kafka connection errors and retries.

### Ticket 2.3: Add Kafka to Local Development Environment

**Title:** Chore: Add Kafka and Zookeeper to the Docker Compose setup

**Description:**
To support local development and testing of the new Kafka-based features, the project's `docker-compose.yml` file needs to be updated to include Kafka and Zookeeper services.

**Tasks:**
1.  Add a Zookeeper service to the `docker-compose.yml` file.
2.  Add a Kafka broker service that depends on the Zookeeper service.
3.  Configure the Kafka service with appropriate ports and environment variables for a single-broker setup.
4.  Update the project's documentation to explain how to run Kafka locally.

## 3. Fix Broken Tests

**Goal:** Repair all failing unit and integration tests to ensure the codebase is stable and reliable.

### Ticket 3.1: Fix Failing Tests in `pipestream-engine`

**Title:** Fix: Correct failing unit tests in `pipestream-engine`

**Description:**
The unit tests in `PipeStreamEngineImplTest` are failing because they don't mock the `PipelineConfigService` dependency. This causes the `PipeStreamEngineImpl` to throw an `IllegalArgumentException` when it can't find the test pipeline configuration.

**Tasks:**
1.  Inject a mock of `PipelineConfigService` into `PipeStreamEngineImplTest` using `@InjectMock`.
2.  In each test method, use `Mockito.when()` to define the behavior of the mocked `PipelineConfigService`.
3.  When `pipelineConfigService.getPipeStep(...)` is called with the test data, it should return a `Uni` containing an `Optional` of a mock `PipelineStepConfig`.
4.  Ensure the mock `PipelineStepConfig` has the necessary data for the tests to pass, such as a `grpcServiceName`.
5.  Run the tests to verify that they all pass.

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

## 6. Remove Hardcoded and "Magic" Values

**Goal:** Improve the configurability and maintainability of the codebase by removing hardcoded values and replacing them with proper configuration or constants.

### Ticket 6.1: Refactor Hardcoded Service Name Checks

**Title:** Refactor: Remove hardcoded service name checks in tests and logic

**Description:**
Several parts of the codebase contain hardcoded checks for specific service names, such as "embedder" and "test-module". This makes the code brittle and difficult to test. These checks should be replaced with a more flexible mechanism, such as configuration properties or annotations.

**Tasks:**
1.  In `SecurityIntegrationTests.java`, refactor the tests to avoid special-casing the "embedder" service. The tests should be data-driven and not rely on hardcoded service names.
2.  In `ModuleWhitelistServiceImpl.java`, remove the hardcoded checks for "test-module". This logic should be handled by the test environment, not the service itself.
3.  In `IntraPipelineLoopValidator.java`, remove the hardcoded check for "pipeline-with-direct-loop".

### Ticket 6.2: Create a Consul Key Management Utility

**Title:** Refactor: Create a dedicated utility for managing Consul keys

**Description:**
The code currently parses Consul keys manually using string splitting and magic array indices. This is error-prone and makes the code hard to read. A dedicated utility should be created to build and parse Consul keys in a type-safe way.

**Tasks:**
1.  Create a new class called `ConsulKeyManager` in the `libraries/consul-client` module.
2.  This class should have methods for building the various types of Consul keys used in the application (e.g., `buildPipelineKey`, `buildModuleKey`).
3.  It should also have methods for parsing keys and extracting the relevant information (e.g., `getPipelineIdFromKey`, `getModuleNameFromKey`).
4.  Update `PipelineConfigService.java` and `ConsulModuleRegistryService.java` to use the new `ConsulKeyManager` instead of manual string manipulation.

### Ticket 6.3: Improve Profile-Specific Logic

**Title:** Refactor: Use type-safe configuration for profile-specific logic

**Description:**
The code currently uses string comparisons to check for the "dev" and "prod" profiles. This is not ideal. A more robust solution would be to use a custom annotation or a dedicated configuration property.

**Tasks:**
1.  Introduce a new configuration property, such as `pipeline.mode`, which can be set to `DEV`, `PROD`, or `TEST`.
2.  Update the code in `RegistrationServiceSelfRegistration.java` and `InMemoryModuleRegistryService.java` to use this new property instead of checking the profile name.
3.  Document the new `pipeline.mode` property in the `CONFIGURATION.md` file.

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

## 10. Improve Python gRPC Connector Script

**Goal:** Make the Python gRPC connector script more robust, flexible, and easier to use.

### Ticket 10.1: Add Command-Line Arguments

**Title:** Feature: Add command-line arguments to the Python gRPC connector

**Description:**
The Python gRPC connector script should be configurable via command-line arguments instead of hardcoded values. This will make it easier to use in different environments and for different testing scenarios.

**Tasks:**
1.  Use the `argparse` module to add command-line arguments for:
    *   `--consul-host`: The address of the Consul server.
    *   `--engine-service`: The name of the pipestream engine service in Consul.
    *   `--num-cases`: The number of cases to send.
    *   `--input-file`: An optional path to a JSON file containing case data.
2.  Update the script to use the values from the command-line arguments instead of the hardcoded values.

### Ticket 10.2: Improve Service Discovery

**Title:** Refactor: Improve service discovery in the Python gRPC connector

**Description:**
The current service discovery mechanism in the Python gRPC connector is manual and not very robust. It should be improved to handle retries and be more resilient to failures.

**Tasks:**
1.  Use the `python-consul` library to interact with Consul in a more structured way.
2.  Implement a retry mechanism with exponential backoff for the service discovery process.
3.  Add better error handling to provide more informative messages when service discovery fails.

### Ticket 10.3: Add gRPC Connection Error Handling

**Title:** Feature: Add error handling for gRPC connections

**Description:**
The script should gracefully handle cases where it cannot connect to the gRPC server.

**Tasks:**
1.  Add a `try...except` block around the `grpc.insecure_channel` call to catch `grpc.RpcError` exceptions.
2.  When a connection error occurs, log an informative error message and exit gracefully.

### Ticket 10.4: Add Support for Reading Cases from a File

**Title:** Feature: Add support for reading case data from a file

**Description:**
To make the connector more useful for testing, it should be able to read case data from a JSON file instead of just using the hardcoded sample cases.

**Tasks:**
1.  Add a function to read a JSON file containing a list of case data objects.
2.  If the `--input-file` command-line argument is provided, the script should use this function to load the case data.
3.  If no input file is provided, the script should fall back to using the hardcoded sample cases.

## 11. Implement Tiered Connector Architecture

**Goal:** Implement the tiered connector architecture based on the existing protobuf definitions.

### Ticket 11.1: Implement Tier 1 Connector Registration

**Title:** Feature: Implement registration for Tier 1 connectors

**Description:**
Implement the server-side logic for the `RegisterConnector` RPC defined in `pipeline_connector_server.proto`. This will allow trusted, client-managed connectors to register with the system.

**Tasks:**
1.  Create a new service, `ConnectorCoordinatorService`, that implements the `ConnectorCoordinator` gRPC service.
2.  Implement the `registerConnector` method to handle `RegisterConnectorRequest` messages.
3.  The implementation should:
    *   Validate the request.
    *   Store the connector's information (type, endpoint, capabilities, configuration) in a persistent data store (e.g., a database or Consul).
    *   Generate a unique `connector_id` and return it in the `RegisterConnectorResponse`.
4.  Add authentication and authorization to the `registerConnector` method to ensure that only trusted clients can register new connectors.

### Ticket 11.2: Implement Tier 2 Connector Polling

**Title:** Feature: Implement polling for Tier 2 connectors

**Description:**
Implement the logic to periodically poll Tier 2 connectors for data. This will involve using the `TriggerSync` RPC and handling the data that is returned.

**Tasks:**
1.  Create a new service, `ConnectorPollingService`, that is responsible for scheduling and triggering sync operations.
2.  This service should periodically call the `TriggerSync` RPC on registered Tier 2 connectors.
3.  The `ConnectorPollingService` should be able to handle both full and incremental syncs, based on the connector's capabilities.
4.  Implement the logic to process the data that is returned from the sync operation, including handling errors and retries.

## 12. Implement Declarative Pre- and Post-Processing Engine

**Goal:** Enhance the `ProtoFieldMapper` to support advanced transformations like field joining and filtering, and integrate it into the `pipestream-engine` to be configured on a per-pipeline basis.

### Ticket 12.1: Enhance `ProtoFieldMapper` to Support Field Joining

**Title:** Feature: Add a `JOIN` function to `ProtoFieldMapper`

**Description:**
Extend the `ProtoFieldMapper` to support a `JOIN` function that can concatenate multiple source fields (or literals) into a single target field, with a specified separator.

**Tasks:**
1.  Update the `RuleParser` to recognize a new function-style syntax, e.g., `target.field = JOIN(" ", source.field1, "literal", source.field2)`.
2.  In the `FieldAccessor`, enhance the `getValue` method to detect the `JOIN(...)` function.
3.  When `JOIN` is detected, the accessor should:
    *   Parse the arguments within the parentheses.
    *   The first argument should be the separator string.
    *   Recursively call `getValue` for each subsequent argument to resolve its value (whether it's a path or a literal).
    *   Concatenate the resolved values using the separator.
    *   Return the final joined string.

### Ticket 12.2: Enhance `ProtoFieldMapper` to Support Filtering

**Title:** Feature: Add `INCLUDE` and `EXCLUDE` rules to `ProtoFieldMapper`

**Description:**
Extend the `ProtoFieldMapper` to support `INCLUDE` and `EXCLUDE` rules for fine-grained control over which fields are present in the final message.

**Tasks:**
1.  **EXCLUDE Rule:**
    *   Add a new `EXCLUDE` operation, e.g., `EXCLUDE target.field.to_remove`.
    *   This will be similar to the existing `CLEAR` operation but provides a more explicit and readable syntax for filtering.
    *   Consider adding support for wildcards in the future, e.g., `EXCLUDE target.metadata.*`.
2.  **INCLUDE Rule:**
    *   This is a more complex operation that implies "remove everything else."
    *   Introduce a new rule syntax, e.g., `target = INCLUDE(source, "field1", "field2.nested")`.
    *   The implementation will involve:
        *   Creating a new, empty instance of the target message.
        *   Iterating through the specified fields to include.
        *   For each field, read the value from the source and set it on the new target message.
        *   Replacing the original target message with this new, filtered instance.

### Ticket 12.3: Integrate `ProtoFieldMapper` into the Pipestream Engine

**Title:** Feature: Integrate `ProtoFieldMapper` into the `pipestream-engine`

**Description:**
Allow pipelines to be configured with pre- and post-processing mapping rules that are executed by the `pipestream-engine`.

**Tasks:**
1.  Update the `PipelineStepConfig` model to include two new optional fields: `pre_processing_rules` and `post_processing_rules`, both of which are lists of strings.
2.  In the `PipeStreamEngineImpl`, before processing a step, check if `pre_processing_rules` are defined. If so, use an instance of `ProtoFieldMapper` to apply the rules to the incoming `PipeDoc`.
3.  After processing a step, check if `post_processing_rules` are defined. If so, use the `ProtoFieldMapper` to apply the rules to the outgoing `PipeDoc`.
4.  Update the pipeline configuration JSON schema and documentation to include these new fields.

## 13. Unified Frontend for Pipeline Step Testing

**Goal:** Create a web-based UI within the `pipestream-engine` to allow developers to test any registered pipeline module by sending input and viewing the output.

### Ticket 13.1: Implement a Schema Discovery Service

**Title:** Feature: Create a service in the engine to expose module schemas

**Description:**
To dynamically generate a UI for any module, the frontend needs access to the input and output schemas for each service. The engine should expose an API endpoint that provides these schemas.

**Tasks:**
1.  Leverage the gRPC reflection capabilities of the modules. The `pipestream-engine` will need a mechanism to query the gRPC service descriptors from each registered module.
2.  Create a new REST endpoint in the `pipestream-engine`, e.g., `GET /api/v1/modules/{serviceName}/schema`.
3.  This endpoint should return a representation of the module's gRPC service, including the request and response message structures, preferably converted to a format like JSON Schema.

### Ticket 13.2: Develop a Dynamic UI Generation Framework

**Title:** Feature: Create a frontend that dynamically generates forms from schemas

**Description:**
Build a frontend application that can consume the schema from the new API endpoint and render an interactive form for the user to input data.

**Tasks:**
1.  Set up a new frontend project within the `pipestream-engine` module (e.g., using React or Vue).
2.  Use a library like `react-jsonschema-form` or a similar tool to automatically generate a web form based on the JSON Schema of a module's input message.
3.  The UI should include fields for all parts of the input message, handling nested messages and repeated fields appropriately.

### Ticket 13.3: Create a Backend API for Step Execution

**Title:** Feature: Implement a backend API to execute single pipeline steps for testing

**Description:**
Create a dedicated REST endpoint in the `pipestream-engine` that the new frontend can call to execute a single step of a pipeline with user-provided data.

**Tasks:**
1.  Create a new endpoint, e.g., `POST /api/v1/steps/{serviceName}/test`.
2.  This endpoint will accept a JSON payload representing the input message for the target service.
3.  The engine will take this payload, construct the appropriate `PipeDoc` or gRPC request, and call the actual module's gRPC service.
4.  The endpoint should return the full output from the module as a JSON response to the frontend.

### Ticket 13.4: Build the Frontend Testing Interface

**Title:** Feature: Build the complete UI for listing and testing modules

**Description:**
Assemble the previously developed components into a cohesive user interface for testing pipeline steps.

**Tasks:**
1.  Create a main view that lists all currently registered modules available for testing.
2.  When a user selects a module, the UI should fetch its schema and display the dynamically generated input form.
3.  Add a "Submit" button that sends the form data to the new test execution API.
4.  Display the JSON response from the API in a clean, readable format, allowing developers to inspect the output of the pipeline step.

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
