# Phased Implementation Plan: Registration Fix and Frontend Tooling

This document outlines the step-by-step plan to fix the module registration service, correct the schema provisioning in modules, and build the new developer tooling for schema-driven UI rendering.

## Phase 1: Fix the Foundation (Java Registration Service)

**Objective:** Repair the `ModuleRegistrationService` to correctly register modules with Consul.

1.  **Analyze Code:** Review `io.pipeline.grpc.service.registration.ModuleRegistrationService` to understand its current logic and identify the source of the suspected infinite recursion and failure to save to Consul.
2.  **Propose & Implement Fix:** Correct the logic to ensure it properly fetches module details, constructs a Consul registration payload, and performs the registration.
3.  **Verify:** Run the service and use Consul's UI or API to confirm that modules are being registered correctly.

## Phase 2: Correct the Schema Provider (Java Parser Module)

**Objective:** Ensure the Parser module provides a raw, unfiltered OpenAPI 3.1 schema.

1.  **Examine `ParserServiceImpl`:** Focus on the `getServiceRegistration` method to identify where the schema is being modified or "cleaned".
2.  **Implement Change:** Modify the service to return the raw, auto-generated OpenAPI schema *before* any filtering or transformation occurs.
3.  **Verify:** Add temporary logging to the `ParserServiceImpl` to print the `json_config_schema` being returned. Run the service and inspect the logs to confirm the schema is complete and unresolved.

## Phase 3: Build the Developer Tool (Node.js Backend)

**Objective:** Create the backend service for the standalone developer tool.

1.  **Scaffold Project:** Create `applications/node/dev-tools/backend` and initialize a Node.js TypeScript project. Install `express`, `@grpc/grpc-js`, and `@apidevtools/json-schema-ref-parser`.
2.  **Implement gRPC Client:** Write a client to connect to a target module (e.g., the fixed Parser) and call its `GetServiceRegistration` method.
3.  **Create Express API:** Build a simple Express server with a REST endpoint (e.g., `/api/module-schema`) that takes a module's gRPC address, uses the gRPC client to fetch the raw schema, and returns it.
4.  **Test Communication:** Run the Parser module and the new Node.js backend to verify end-to-end communication.

## Phase 4: Implement the UI (Vue.js Frontend)

**Objective:** Build the user-facing frontend for the developer tool.

1.  **Scaffold Project:** Create `applications/node/dev-tools/frontend` and initialize a Vue 3 project using Vite. Install `jsonforms-vue`.
2.  **Build Core UI:** Create a main application component with an input for the module's address and a button to fetch the schema from the Node.js backend.
3.  **Implement `UniversalConfigCard.vue`:** Develop the reusable component that accepts a JSON schema and renders the configuration form using JSONForms.
4.  **End-to-End Test:** Run the full stack (Parser -> Node.js Backend -> Vue.js Frontend) to confirm the entire workflow is functional and the parser's configuration card renders correctly.
