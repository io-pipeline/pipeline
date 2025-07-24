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
