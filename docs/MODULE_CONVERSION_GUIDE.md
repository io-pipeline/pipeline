# Module Conversion Guide: Building a Robust & Testable Rokkon Ecosystem

**Last Updated: July 8, 2025**

This guide provides a comprehensive, step-by-step process for converting existing and new Rokkon modules to align with our refined architecture, testing strategy, and development practices. The goal is to ensure modules are easily testable, maintainable, and integrate seamlessly into the Rokkon Pipeline Engine.

---

## I. Core Principles: Driving Development & Testing

Our development and testing strategy is built upon the following guiding principles, designed to provide fast feedback, reliable integration, and high confidence in our system:

*   **Fast Feedback:** Developers should spend most of their time running fast, isolated tests that don't rely on external infrastructure.
*   **Leverage Quarkus Extensions:** We utilize Quarkus's powerful extensions for seamless integration with external services (e.g., Consul, Kafka) and for simplifying testing with built-in test utilities.
*   **Reliable Integration:** Interactions with external systems are critical. Integration tests use real, ephemeral instances of these services (via Testcontainers) to validate this "glue code" reliably and in isolation.
*   **Full System Confidence:** End-to-end tests provide the ultimate confidence by verifying complete user workflows across the entire deployed system, just as it would run in production.
*   **Clean Architecture:** Strict separation of concerns, clear dependency graphs, and avoidance of circular dependencies are paramount.

---

## II. Prerequisites

Before converting a module, ensure the following foundational elements are in place:

*   **Stable Core Engine:** The `engine:pipestream` and its core dependencies (`engine:validators`, `engine:consul`, `engine:dynamic-grpc`) must be compiling, passing their tests, and capable of running in Quarkus Dev Mode.
*   **Understanding of `TESTING_STRATEGY.md`:** Familiarity with the concepts outlined in `TESTING_STRATEGY.md` is essential, especially regarding the layered testing approach and the `UnifiedTestProfile`.

---

## III. Module Conversion Phases

This section details the step-by-step process for converting a module.

### Phase 1: Initial Setup & Dependency Alignment

The goal of this phase is to integrate the module into the Gradle build system and align its dependencies with our established BOMs and conventions.

1.  **Add Module to `settings.gradle.kts`:**
    *   Include the module in the `include` block of the root `settings.gradle.kts` file. For example:
        ```kotlin
        // settings.gradle.kts
        include(
            // ... existing modules
            ":modules:your-new-module"
        )
        ```

2.  **Update `build.gradle.kts`:**
    *   **Apply Quarkus Plugin:** Ensure the `id("io.quarkus")` plugin is applied.
    *   **Use Module BOM:** Replace any direct Quarkus BOM imports with `implementation(platform(project(":bom:module")))`. This ensures the module inherits all standard dependencies from our curated Bill of Materials.
        ```kotlin
        // modules/your-new-module/build.gradle.kts
        plugins {
            java
            id("io.quarkus")
            // ... other plugins like maven-publish, idea
        }

        dependencies {
            // Use the Module BOM for standard module dependencies
            implementation(platform(project(":bom:module")))

            // Explicitly depend on commons:grpc-stubs if you consume generated protobufs
            // This is crucial if your module uses gRPC services defined in commons:protobuf
            implementation(project(":commons:grpc-stubs"))

            // Add any other module-specific dependencies not covered by the BOM
            // e.g., implementation("your.library:some-dependency")
        }
        ```
    *   **Remove Redundant Dependencies:** After applying the BOM, remove any dependencies that are now transitively provided by `bom:module`. This cleans up the build file and prevents version conflicts.
    *   **Disable gRPC Code Generation (if consuming stubs):** If your module consumes pre-generated gRPC stubs from `commons:grpc-stubs` (which is the standard practice), you *must* disable gRPC code generation within the module itself to prevent split package issues.
        ```kotlin
        // modules/your-new-module/build.gradle.kts
        quarkus {
            // ... other Quarkus configurations
            quarkusBuildProperties.put("quarkus.generate-code.grpc.scan-for-proto", "none")
        }
        ```

3.  **Configuration (`src/main/resources/application.yml`):**
    *   **Set Module-Specific Port:** Assign a unique port in the `391xx` range for your module. Use environment variable defaults for flexibility.
        ```yaml
        quarkus:
          http:
            port: ${PORT:391XX} # Replace XX with your module's unique identifier
          grpc:
            server:
              port: ${PORT:391XX} # Unified server mode
        ```
    *   **Remove Hardcoded OTEL Endpoints:** Ensure no hardcoded OpenTelemetry (OTEL) endpoints. Dev Services will handle this automatically.
    *   **Delete `application-dev.yml`:** Consolidate all configuration into the main `application.yml`. Use Quarkus profiles (`%dev`, `%test`) as needed within this single file.

### Phase 2: Test Refactoring (The Core of the Conversion)

This is the most critical phase, ensuring your module's tests align with our fast, reliable, and layered testing strategy.

1.  **Identify Test Types:**
    *   **Unit Tests:** Verify isolated logic, no external dependencies.
    *   **Component Tests:** Verify component interaction within Quarkus context, external dependencies mocked.
    *   **Integration Tests:** Verify interaction with real external services (e.g., Consul, Kafka) using Testcontainers.
    *   **End-to-End (E2E) Tests:** Verify full system workflows (covered in a later phase).

2.  **Organize Test Files:**
    *   **Unit & Component Tests:** Place in `src/test/java/com/rokkon/pipeline/yourmodule/`.
    *   **Integration Tests:** Place in `src/integrationTest/java/com/rokkon/pipeline/yourmodule/`. Ensure these files end with `IT.java` (e.g., `YourServiceIT.java`).

3.  **Implement Test Patterns:**

    *   **Base Test Classes:**
        *   Create abstract base classes (e.g., `YourServiceTestBase.java`) in `src/test/java` to define common test logic and abstract methods for dependency injection.
        *   These base classes should *not* have Quarkus annotations (`@QuarkusTest`, `@Inject`).
        ```java
        // Example: YourServiceTestBase.java
        public abstract class YourServiceTestBase {
            protected abstract YourService getYourService(); // Abstract getter for the service under test

            // Common test methods that use getYourService()
            @Test
            void testSomethingCommon() {
                // ... test logic
            }
        }
        ```

    *   **Unit Tests (`@QuarkusTest`):**
        *   Extend the appropriate base test class.
        *   Annotate with `@QuarkusTest` and `@TestProfile(UnifiedTestProfile.class)`. The `UnifiedTestProfile` ensures Consul and other external services are disabled by default.
        *   **Use `@InjectMock` for *all* external dependencies.** This is crucial for isolation and speed. Mock `ConsulClient`, `KafkaProducer`, other module clients, etc.
        *   **Use `Uni` for Reactive Operations:** All asynchronous operations should use SmallRye Mutiny's `Uni` type. When you need to block for a result in a test, use `await().indefinitely()`.
        ```java
        // Example: YourServiceUnitTest.java
        @QuarkusTest
        @TestProfile(UnifiedTestProfile.class) // Disables Consul, etc.
        class YourServiceUnitTest extends YourServiceTestBase {

            @InjectMock // Mock the external dependency
            ConsulClient mockConsulClient;

            @Inject // Inject the service under test
            YourService yourService;

            @BeforeEach
            void setupMocks() {
                // Define mock behavior for yourService's dependencies
                when(mockConsulClient.putValue(anyString(), anyString()))
                    .thenReturn(Uni.createFrom().item(true));
            }

            @Override
            protected YourService getYourService() {
                return yourService;
            }

            @Test
            void testYourServiceLogic() {
                // Test yourService, which will use the mockedConsulClient
                yourService.saveData("some-key", "some-value").await().indefinitely();
                verify(mockConsulClient).putValue("some-key", "some-value");
            }
        }
        ```

    *   **Integration Tests (`@QuarkusIntegrationTest`):**
        *   Extend the appropriate base test class.
        *   Annotate with `@QuarkusIntegrationTest` and `@QuarkusTestResource(ConsulTestResource.class)` (or other Testcontainers resources like `KafkaContainer`, `PostgreSQLContainer`).
        *   **Use real `Uni` operations against real Testcontainers instances.** Do *not* mock external services here.
        *   **Isolation with "Named Jails":** For tests interacting with shared resources like Consul KV, use a unique prefix (a "named jail") for each test or test suite to prevent interference. The `UnifiedTestProfile` already supports this for Consul KV.
        ```java
        // Example: YourServiceIT.java
        @QuarkusIntegrationTest
        @QuarkusTestResource(ConsulTestResource.class) // Starts a real Consul container
        class YourServiceIT extends YourServiceTestBase {

            @Inject // Inject the real service
            YourService yourService;

            @Inject // Inject the real ConsulClient from the test resource
            ConsulClient realConsulClient;

            @Override
            protected YourService getYourService() {
                return yourService;
            }

            @Test
            void testYourServiceWithRealConsul() {
                // Test yourService, which will interact with the real Consul instance
                yourService.saveData("it-key", "it-value").await().indefinitely();

                // Verify directly with the real Consul client
                Optional<KeyValue> kv = realConsulClient.getValue("it-key").await().indefinitely();
                assertThat(kv).isPresent();
                assertThat(kv.get().getValue()).isEqualTo("it-value");
            }
        }
        ```

### Phase 3: Docker Image & Deployment (Future Consideration)

This phase is **deferred** until the core engine and the initial dashboard are stable.

*   **Module Docker Image:** The goal is a standard Docker image for each module that includes:
    *   The module's executable JAR (`your-module-runner.jar`).
    *   The `pipeline-cli.jar` (from `:cli:register-module`).
    *   A `module-entrypoint.sh` script that orchestrates the module's startup and automated registration with the engine.
*   **Automated Registration:** The `module-entrypoint.sh` will start the module's gRPC service, wait for it to become healthy, and then execute `java -jar pipeline-cli.jar register ...` to register the module with the running Pipeline Engine.
*   **Consolidation:** Docker-related code will be consolidated in `testing:server-util` for reusability and clean separation from production code.

---

## IV. Benefits of This Approach

*   **Fast Developer Feedback:** Unit tests run in milliseconds, enabling rapid iteration.
*   **Reliable CI/CD:** Integration tests use isolated, real environments, preventing flaky builds.
*   **Clean Architecture:** Clear separation of concerns, promoting maintainability and scalability.
*   **Consistent Testing:** Standardized patterns across all modules.
*   **Leverage Quarkus:** Full utilization of Quarkus's testing and development features.
*   **Accelerated UI Development:** The `test-module` and a stable backend will provide a solid foundation for frontend work.

---

## V. Running Multiple Modules (Local Development)

For local development, each module can be run in its own terminal using `quarkusDev`.

```bash
# Terminal 1: Run the main engine
./gradlew :engine:pipestream:quarkusDev

# Terminal 2: Run your module
./gradlew :modules:your-module:quarkusDev

# Terminal 3: Run another module
./gradlew :modules:chunker:quarkusDev
```

All modules will:
*   Share the same automatically started Consul DevService instance.
*   Share the same automatically started OpenTelemetry/Grafana instance for observability.
*   Discover each other automatically via Stork/Consul integration.

---

## VI. Future Considerations

*   **Generic Test Harness (`test-module` Evolution):** The `modules:test-module` will evolve into a powerful, generic test harness. It will be capable of:
    *   Dynamically loading and wrapping other modules for testing.
    *   Running standard test scenarios against any module.
    *   Exposing rich metrics and telemetry for the dashboard.
*   **Kafka Integration:** Once the gRPC data plane is stable, Kafka will be integrated as a pluggable transport mechanism for asynchronous pipelines.
*   **WireMock for Frontend Development:** While not a current priority, if frontend development faces significant blockers due to backend instability or slow deployments, a WireMock setup could be considered. However, the `test-module` and a stable backend should provide sufficient decoupling for initial UI development.
