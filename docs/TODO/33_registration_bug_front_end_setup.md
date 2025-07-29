# Phased Implementation Plan: Registration Fix and Frontend Tooling

This document outlines the step-by-step plan to fix the module registration service, correct the schema provisioning in modules, and build the new developer tooling for schema-driven UI rendering.

## Phase 1: Fix the Foundation (Java Registration Service)

**Objective:** Repair the `ModuleRegistrationService` to correctly register modules with Consul.

1.  **Analyze Code:** Review `io.pipeline.grpc.service.registration.ModuleRegistrationService` to understand its current logic and identify the source of the suspected infinite recursion and failure to save to Consul.
2.  **Propose & Implement Fix:** Correct the logic to ensure it properly fetches module details, constructs a Consul registration payload, and performs the registration.
3.  **Verify:** Run the service and use Consul's UI or API to confirm that modules are being registered correctly.

## Phase 2: Standardize Schema Provisioning Across All Modules

**Objective:** Ensure all modules provide schemas via the `json_config_schema` field in their gRPC response.

1.  **Parser Module:** ✅ Already provides full OpenAPI 3.1 schema
2.  **Chunker Module:** Fix to provide full OpenAPI schema (not just partial JSON Schema)
3.  **Echo Module:** Add `json_config_schema` field to `getServiceRegistration` response
4.  **Other Modules:** Review and add schema field as needed
5.  **Fallback Behavior:** Modules without schemas will get generic key/value editor in UI

**Note:** This is a non-breaking change - existing validation continues to work while we add the new field.

## Phase 3: Build the Developer Tool (Node.js Backend)

**Objective:** Create the backend service for the standalone developer tool.

1.  **Scaffold Project:** Create `applications/node/dev-tools/backend` and initialize a Node.js TypeScript project. Install `express`, `@grpc/grpc-js`, and `@apidevtools/json-schema-ref-parser`.
2.  **Implement gRPC Client:** Write a client to connect to a target module (e.g., the fixed Parser) and call its `GetServiceRegistration` method.
3.  **Create Express API:** Build a simple Express server with a REST endpoint (e.g., `/api/module-schema`) that takes a module's gRPC address, uses the gRPC client to fetch the raw schema, and returns it.
4.  **Test Communication:** Run the Parser module and the new Node.js backend to verify end-to-end communication.

## Phase 4: Implement the UI (Vue.js Frontend)

**Objective:** Build the user-facing frontend for the developer tool.

1.  **Scaffold Project:** ✅ Create `applications/node/dev-tools/frontend` and initialize a Vue 3 project using Vite. Install `jsonforms-vue`.
2.  **Build Core UI:** ✅ Create a main application component with an input for the module's address and a button to fetch the schema from the Node.js backend.
3.  **Implement `UniversalConfigCard.vue`:** ✅ Develop the reusable component that accepts a JSON schema and renders the configuration form using JSONForms.
4.  **Add Fallback UI:** Implement key/value editor for modules without schemas.
5.  **End-to-End Test:** Run the full stack to confirm the entire workflow is functional.

## Phase 5: Implement Sample Data Testing

**Objective:** Enable testing modules with real data through the developer tool.

1.  **Java Helper Service:** Create utility to generate ModuleProcessRequest .bin files from test documents
2.  **Frontend File Loading:** Add ability to load .bin protobuf files in the UI
3.  **Module Testing:** Send loaded requests to modules and display responses
4.  **Pipeline Chaining:** Save module outputs as inputs for next module
5.  **Dataset Creation:** Build complete test dataset covering all modules
