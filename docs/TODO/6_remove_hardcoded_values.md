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
