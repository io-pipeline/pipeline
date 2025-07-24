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
