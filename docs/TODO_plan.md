# Project Cleanup Plan

## 0. Jackson/JSON Naming Convention Strategy ✅ **COMPLETED**

**Goal:** Establish a consistent approach to handling JSON naming conventions across the API to support both camelCase (Java/JavaScript) and snake_case (Python) clients.

### ✅ **COMPLETED**: Standardize JSON Property Naming Strategy

**Status:** **IMPLEMENTED** - ChunkerConfig now supports both naming conventions with `@JsonProperty("config_id")` and `@JsonAlias({"configId"})` plus `@JsonCreator` for auto-generation.

**What was implemented:**
- ChunkerConfig uses `@JsonProperty("config_id")` (snake_case primary) with `@JsonAlias({"configId"})` (camelCase alias)
- `@JsonCreator` auto-generates configId when missing from JSON, preventing NPE issues
- Both Python clients (snake_case) and Java/JavaScript clients (camelCase) are now supported
- Technical debt documented for future API consistency improvements

**Future work:** Document naming convention guidelines and consider API versioning for standardization.

## 1. Gradle Consolidation ✅ **COMPLETED**

**Goal:** Centralize Gradle configuration to simplify maintenance and ensure consistency across all modules.

### ✅ **COMPLETED**: Ticket 1.1: Consolidate Repository Definitions

**Title:** Refactor: Centralize Maven repository definitions in root Gradle files

**Status:** **IMPLEMENTED** - All subproject `repositories` blocks removed, centralized in root `build.gradle` `allprojects` block.

**What was completed:**
- ✅ Removed `repositories` blocks from all subproject `build.gradle` files
- ✅ Root `build.gradle` `allprojects` block is now single source of truth for repositories
- ✅ `pluginManagement` block in `settings.gradle` correctly defines plugin repositories
- ✅ Project builds and resolves dependencies correctly after changes

### ✅ **COMPLETED**: Ticket 1.2: Centralize Proxy Settings

**Title:** Feature: Add centralized proxy configuration for Gradle

**Status:** **IMPLEMENTED** - Proxy configuration template added to `gradle.properties`.

**What was completed:**
- ✅ Added standard Gradle proxy properties to root `gradle.properties` file:
    ```properties
    # Proxy settings (uncomment and configure if needed)
    #systemProp.http.proxyHost=your.proxy.host
    #systemProp.http.proxyPort=8080
    #systemProp.http.proxyUser=your_user
    #systemProp.http.proxyPassword=your_password
    #systemProp.https.proxyHost=your.proxy.host
    #systemProp.https.proxyPort=8080
    #systemProp.https.proxyUser=your_user
    #systemProp.https.proxyPassword=your_password
    ```

### ✅ **COMPLETED**: Ticket 1.3: Standardize Plugin Application

**Title:** Refactor: Standardize Gradle plugin application using version catalog aliases

**Status:** **IMPLEMENTED** - All subprojects now use `alias(libs.plugins.*)` syntax with version catalog.

**What was completed:**
- ✅ All `build.gradle` files in subprojects reviewed and updated
- ✅ All plugins now use `alias(libs.plugins.*)` syntax (e.g., `alias(libs.plugins.quarkus)`, `alias(libs.plugins.jandex)`)
- ✅ Version catalog in `gradle/libs.versions.toml` contains all plugin aliases
- ✅ Project builds correctly with standardized plugin application
- ✅ BOM (Bill of Materials) implemented in `bom/build.gradle` for dependency management

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

## 15. Formalize REST APIs for Pipeline Designer and Admin Tools

**Goal:** Evolve the existing demo REST APIs into a robust, secure, and well-documented API layer that can power a visual pipeline designer and administrative frontends.

### Ticket 15.1: Refactor Existing Test APIs

**Title:** Refactor: Formalize and secure existing test/demo REST endpoints

**Description:**
The current REST APIs used for testing and demos should be refactored into production-quality endpoints. This involves moving them to a proper API path, adding security, and improving error handling.

**Tasks:**
1.  Move all test-related endpoints under a consistent, versioned path, such as `/api/v1/admin/...` or `/api/v1/designer/...`.
2.  Implement authentication and authorization for these endpoints to ensure only administrative users can access them.
3.  Replace any "hacky" implementations with robust, production-ready code, including proper input validation and structured error responses.

### Ticket 15.2: Implement REST APIs for Pipeline Lifecycle Management

**Title:** Feature: Create REST APIs to manage pipelines through their lifecycle

**Description:**
To support a visual designer, a comprehensive set of REST APIs is needed to allow for the creation, configuration, and promotion of pipelines through the `DESIGN`, `TESTING`, and `PRODUCTION` stages.

**Tasks:**
1.  **Create/Update Pipeline:** Implement `POST /api/v1/pipelines` and `PUT /api/v1/pipelines/{pipelineId}` endpoints that accept a full pipeline configuration in JSON format.
2.  **Get Pipeline:** Implement a `GET /api/v1/pipelines/{pipelineId}` endpoint to retrieve the current configuration of a pipeline.
3.  **Promote Pipeline:** Implement a `POST /api/v1/pipelines/{pipelineId}/promote` endpoint. This endpoint will handle the logic of moving a pipeline from `DESIGN` to `TESTING`, or from `TESTING` to `PRODUCTION`, which may involve updating Consul configurations and activating services.
4.  **List Pipelines:** Implement a `GET /api/v1/pipelines` endpoint to list all pipelines and their current status.

### Ticket 15.3: Implement REST APIs for Operational Control

**Title:** Feature: Create REST APIs for operational control of pipelines and listeners

**Description:**
To power the admin frontend, REST endpoints are needed to perform operational tasks like pausing and resuming services.

**Tasks:**
1.  Implement a `POST /api/v1/listeners/{listenerGroup}/_pause` endpoint to pause all Kafka listeners within a specific group.
2.  Implement a `POST /api/v1/listeners/{listenerGroup}/_resume` endpoint to resume all Kafka listeners within a specific group.
3.  Implement a `GET /api/v1/pipelines/{pipelineId}/status` endpoint to provide detailed monitoring information about a running pipeline.

## 16. Implement Administrative and Designer Frontend

**Goal:** Create a comprehensive web frontend using Quinoa and Vue that provides a visual pipeline designer and operational controls for administrators.

### Ticket 16.1: Set Up Quinoa and Vue Frontend Project

**Title:** Chore: Initialize the Quinoa and Vue.js frontend application

**Description:**
Set up the project structure for the new frontend application within the `pipestream-engine` or a new dedicated module.

**Tasks:**
1.  Add the Quarkus Quinoa extension to the project.
2.  Configure Quinoa to use Vue.js as the frontend framework.
3.  Establish the basic project structure, including component directories, routing, and state management (e.g., Pinia).

### Ticket 16.2: Build the Visual Pipeline Designer

**Title:** Feature: Create a visual, drag-and-drop pipeline designer

**Description:**
Develop the UI for creating and configuring pipelines. This will be the primary interface for users in `DESIGN` mode.

**Tasks:**
1.  Create a component that fetches the list of available modules (and their schemas) from the `/api/v1/modules` endpoint (from Ticket 13.1).
2.  Implement a drag-and-drop interface where users can place modules onto a canvas to build a pipeline.
3.  When a user adds a module to the pipeline, display a configuration panel where they can set the module's parameters and the pre/post-processing rules (`ProtoFieldMapper`).
4.  Implement the logic to save the designed pipeline by calling the `POST /api/v1/pipelines` endpoint.

### Ticket 16.3: Build the Administrative Dashboard

**Title:** Feature: Create the administrative dashboard for pipeline operations

**Description:**
Develop the UI for administrators to monitor and control pipelines in `TESTING` and `PRODUCTION`.

**Tasks:**
1.  Create a dashboard view that lists all pipelines and their current status, using the `GET /api/v1/pipelines` endpoint.
2.  For each pipeline, provide controls to view detailed status, logs, and metrics.
3.  Implement the UI for pausing and resuming Kafka listener groups by calling the new operational REST APIs.
4.  Add controls for promoting pipelines from one stage to the next.

## 17. Implement Streaming gRPC Architecture for Large Document Processing

**Goal:** Replace unary gRPC calls with streaming interfaces to handle large documents efficiently and eliminate message size limitations.

### Background
Current architecture sends entire documents (potentially 3,202+ chunks) as single gRPC messages, causing:
- `RESOURCE_EXHAUSTED: gRPC message exceeds maximum size 4194304` errors
- Memory pressure and timeout issues
- Blocking operations that don't leverage Quarkus reactive capabilities

### Design Decision: Always Streaming Architecture

**Strategy:** Implement streaming alongside existing unary interfaces for backward compatibility and gradual migration.

#### Core Principles:
1. **Always Stream** - No thresholds, no conditional logic
2. **Natural Backpressure** - Let Mutiny handle reactive flow control
3. **Engine-Controlled** - Engine orchestrates streaming, modules focus on domain logic
4. **Backward Compatible** - Keep existing unary interfaces alongside new streaming

#### Architecture Flow:
```
Engine → ALWAYS uses streaming interface → Module
       → [chunk batch 1] → [results 1]
       → [chunk batch 2] → [results 2]  
       → [chunk batch N] → [results N]
       → Reassembles final result
```

#### Implementation Plan:

### Ticket 17.1: Enhance Proto Interface Usage

**Title:** Implement streaming gRPC methods alongside existing unary methods

**Description:**
The proto interface already supports both unary (`processData`) and streaming (`processDataStream`) methods. Implement the streaming variants in all modules while keeping existing implementations.

**Tasks:**
1. **Module Implementation**: Implement `processDataStream()` in all modules (embedder, chunker, parser, etc.)
2. **Streaming Logic**: `Multi<Chunk> → Multi<Result>` reactive processing
3. **Preserve Ordering**: Ensure chunk-to-result mapping maintains document integrity
4. **Error Handling**: Graceful failure handling in streaming context

### Ticket 17.2: Engine Streaming Orchestration

**Title:** Add streaming support to PipeStreamEngine with dual-mode operation

**Description:**
Enhance the engine to support both unary and streaming gRPC calls with configuration-controlled selection.

**Tasks:**
1. **Dual Interface Support**: Engine can call either unary or streaming methods
2. **Batching Strategy**: Engine controls chunk batch sizes for optimal performance
3. **Configuration**: Feature flags to control streaming vs unary per module type
4. **Result Reassembly**: Collect streaming results back into final document format

### Ticket 17.3: Performance Testing and Migration

**Title:** A/B test streaming vs unary performance and implement gradual migration

**Description:**
Compare performance characteristics and implement safe migration path.

**Tasks:**
1. **Benchmark Testing**: Compare streaming vs unary with large documents (1000+ chunks)
2. **Memory Profiling**: Measure memory usage improvements
3. **Configuration Migration**: Gradual rollout strategy with rollback capability
4. **Documentation**: Update integration guides for streaming architecture

### Benefits:
- **Eliminates message size errors** - No more 4MB/2GB limits needed
- **Better memory utilization** - Process chunks as they arrive
- **Natural backpressure** - Reactive streams handle flow control
- **Quarkus-aligned** - Leverages Mutiny reactive programming fully
- **Scalable** - Handles documents of any size gracefully

## 18. Implement Hybrid Development and Testing Strategy

**Goal:** Implement the hybrid development and testing strategy as outlined in `docs/docker-strategy.md`, using a `compose-devservices.yml` file to manage backing services and leveraging Quarkus profiles to control application behavior.

### Ticket 17.1: Create `compose-devservices.yml`

**Title:** Chore: Create and configure `compose-devservices.yml` for local development

**Description:**
Create a `compose-devservices.yml` file in the root of the project to manage backing services like Consul and Kafka. This file will be used by Quarkus Dev Services to provide a consistent local development environment.

**Tasks:**
1.  Create a new file named `compose-devservices.yml` in the project root.
2.  Add a service definition for Consul, using a specific version (e.g., `1.18.0`) and exposing the necessary ports.
3.  Add a `healthcheck` to the Consul service definition to ensure that Quarkus waits for Consul to be fully ready before starting the application.
4.  Add service definitions for any other backing services that are required for local development (e.g., Kafka, databases).

### Ticket 17.2: Configure Application Profiles

**Title:** Refactor: Configure application profiles to disable `consul-config` in dev and test

**Description:**
Use Quarkus configuration profiles to disable the `quarkus-config-consul` extension in the `dev` and `test` profiles. This will prevent the startup paradox and allow the application to start successfully when using Dev Services.

**Tasks:**
1.  In the `application.properties` file, add the following configuration:
    ```properties
    # Disable consul-config in dev and test modes
    %dev,test.quarkus.consul-config.enabled=false
    ```
2.  Ensure that any configuration that would normally come from Consul is provided locally in the `application.properties` file for the `dev` and `test` profiles.
3.  The production environment will enable the `consul-config` extension by setting the `QUARKUS_CONSUL_CONFIG_ENABLED=true` environment variable.

### Ticket 17.3: Configure Stork for Service Discovery

**Title:** Chore: Configure Stork to use Consul for service discovery

**Description:**
Configure SmallRye Stork to use Consul for service discovery. In the `dev` and `test` profiles, Stork will automatically connect to the Consul instance provided by Dev Services.

**Tasks:**
1.  In the `application.properties` file, add the necessary Stork configuration for each service that needs to be discovered. For example:
    ```properties
    # Configure Stork to use Consul for discovering 'my-other-service'.
    # In dev/test profiles, Quarkus Dev Services will automatically configure
    # the consul-host and consul-port for Stork.
    quarkus.stork.my-other-service.service-discovery.type=consul
    ```
2.  Ensure that the production configuration for Stork points to the correct Consul instance.

## 19. Implement Retry and Backoff Strategies for Tika Parser

**Goal:** Add resilience patterns to the Tika parser to handle document parsing failures, timeout issues, and resource exhaustion that commonly occur in production environments.

### Background
The parser module processes diverse document formats using Apache Tika, which can fail due to:
- **Document format corruption** - Malformed PDFs, Office docs with embedded graphics issues
- **Resource exhaustion** - Large documents consuming excessive memory/CPU  
- **Tika internal errors** - EMF parser AssertionErrors, font mapping failures
- **Processing timeouts** - Complex documents exceeding parse time limits
- **Transient failures** - Temporary I/O issues or library state problems

Since "almost all data connectors start with a tika parse", parser failures can cascade and impact the entire pipeline system.

### Ticket 19.1: Implement Configurable Retry Strategy with Exponential Backoff

**Title:** Feature: Add retry mechanism with exponential backoff for parser failures

**Description:**
Implement a configurable retry strategy that can handle different types of Tika parsing failures with appropriate backoff and fallback mechanisms.

**Tasks:**
1. **Retry Configuration**: Add retry settings to `ParserConfig` record:
   - `maxRetries` (default: 3)
   - `initialBackoffMs` (default: 100ms)  
   - `maxBackoffMs` (default: 5000ms)
   - `retryMultiplier` (default: 2.0)
   - `retryableExceptions` (configurable list of exception types)

2. **Retry Logic Implementation**: 
   - Wrap Tika parsing calls with retry logic using Mutiny's `retry()` operator
   - Implement exponential backoff with jitter to prevent thundering herd
   - Log retry attempts with context (document ID, attempt number, exception)

3. **Exception Classification**:
   - **Retryable**: `TikaException`, `IOException`, transient parsing errors
   - **Non-retryable**: `AssertionError`, `OutOfMemoryError`, configuration errors
   - **Document-specific**: EMF parser issues, corrupted document formats

### Ticket 19.2: Implement Alternative Parser Configuration Fallback

**Title:** Feature: Add fallback parser configurations for failed documents

**Description:**
When documents fail with the primary parser configuration, automatically retry with progressively more conservative parsing settings.

**Tasks:**
1. **Fallback Configuration Chain**:
   - **Primary**: Full parsing with all features enabled
   - **Conservative**: Disable EMF parser, reduce recursion depth, limit metadata extraction
   - **Minimal**: Text-only extraction, no embedded document processing
   - **Emergency**: Plain text fallback with encoding detection only

2. **Smart Fallback Selection**:
   - Analyze exception types to select appropriate fallback level
   - EMF parser errors → Conservative mode (disable EMF parsing)
   - Memory errors → Minimal mode (reduce processing complexity)
   - Timeout errors → Emergency mode (fastest possible extraction)

3. **Configuration Templates**:
   - Pre-defined `ParserConfig` instances for each fallback level
   - Configurable via application properties for different document types
   - Document type detection to select optimal primary configuration

### Ticket 19.3: Implement Circuit Breaker Pattern for Parser Health

**Title:** Feature: Add circuit breaker to prevent cascading parser failures

**Description:**
Implement circuit breaker pattern to temporarily disable problematic parser configurations when failure rates exceed thresholds.

**Tasks:**
1. **Circuit Breaker Configuration**:
   - Failure rate threshold (default: 50% over 10 documents)
   - Open circuit duration (default: 30 seconds)
   - Half-open state test period (default: 5 documents)
   - Per-document-type circuit breakers for granular control

2. **Circuit State Management**:
   - Track success/failure rates per document type and parser configuration
   - Automatically open circuit when failure thresholds exceeded
   - Test circuit recovery with limited document processing
   - Emit metrics and alerts for circuit state changes

3. **Graceful Degradation**:
   - When circuit is open, immediately use most conservative fallback
   - Preserve error context and provide meaningful error messages
   - Allow manual circuit reset via management endpoint

### Ticket 19.4: Add Comprehensive Parser Monitoring and Alerting

**Title:** Feature: Implement detailed monitoring for parser performance and failures

**Description:**
Add comprehensive observability to track parser health, performance patterns, and failure modes for proactive issue resolution.

**Tasks:**
1. **Parser Metrics**:
   - Parse success/failure rates by document type and size
   - Parse duration histograms and percentiles  
   - Retry attempt distributions and backoff effectiveness
   - Circuit breaker state changes and recovery patterns
   - Memory usage patterns during document processing

2. **Structured Logging**:
   - Contextual logging with document metadata (type, size, source)
   - Failure categorization (transient vs persistent, retryable vs terminal)
   - Performance logging for documents exceeding duration thresholds
   - Correlation IDs for tracking document processing across retries

3. **Health Check Enhancement**:
   - Proactive health checks with sample documents of different types
   - Circuit breaker state reporting in health endpoints
   - Parser configuration validation and compatibility checks
   - Resource utilization monitoring (memory, CPU, file handles)

### Benefits:
- **Improved Reliability**: Automatic recovery from transient Tika parsing failures
- **Cascading Failure Prevention**: Circuit breakers protect downstream services
- **Better Resource Utilization**: Progressive fallback reduces resource contention
- **Operational Visibility**: Comprehensive monitoring enables proactive issue resolution
- **System Resilience**: Graceful degradation maintains service availability during parser issues