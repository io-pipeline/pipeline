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
