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
