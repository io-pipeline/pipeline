
# RFC 001: Engine-Centric Schema Transformation and Rendering

**Status:** Proposed
**Author:** Gemini
**Date:** 2025-07-24

## Executive Summary

To support a fully generic, schema-driven UI for module configuration, this document proposes an engine-centric architecture for schema transformation. The core idea is to simplify the requirements for module developers by offloading the complex task of schema processing to the Pipeline Engine and a new shared utility library.

This approach ensures that modules, especially those written in non-Java languages, can be developed with minimal effort while still benefiting from a rich, auto-generated testing and configuration UI.

## The Problem

The system requires two different representations of a module's configuration schema:

1.  **A Rendering Schema:** A fully-resolved OpenAPI 3.1 schema with all `$ref`s expanded. This is required by the JSONForms library to dynamically render a configuration UI.
2.  **A Validation Schema:** A "clean" schema compatible with JSON Schema Draft 7. This is used by the backend for data validation.

The current implementation fails for modules with complex, nested configuration objects because it tries to use a single, cleaned schema for both purposes, which leaves the rendering engine with insufficient information.

## The Proposal: A Hybrid Transformation Model

This proposal shifts the responsibility of schema transformation from individual modules to the central Pipeline Engine and a new developer tool. The developer tool will also produce a shared UI component for use in the engine.

### 1. The Module Developer Contract (The "Golden Rule")

The contract for a module developer, regardless of language, is simplified to its absolute minimum:

*   A module MUST implement the `PipeStepProcessor` gRPC service.
*   Its `GetServiceRegistration` method MUST return a `ServiceRegistrationResponse`.
*   The `json_config_schema` field within this response MUST contain the module's complete, standard, **unresolved OpenAPI 3.1 schema**.

This is the **only** schema-related requirement for a module.

### 2. The New Developer Tool & Shared Component

A new, standalone developer tool will be created to provide an immediate, zero-effort way for developers to test their modules. This tool will be responsible for developing and hardening a reusable Vue component that can render a configuration UI from a schema.

#### Technology Stack

*   **Backend:** Node.js with Express.js, using TypeScript.
*   **Frontend:** Vue 3 with TypeScript.
*   **gRPC Communication:** The backend will use `@grpc/grpc-js` to communicate with the target module. The frontend will use gRPC-Web (`@protobuf-ts/grpcweb-transport`) to communicate with the backend.
*   **Schema Processing:** The Node.js backend will use `@apidevtools/json-schema-ref-parser` to resolve `$ref`s in the OpenAPI schema.
*   **Form Rendering:** The frontend will use `@jsonforms/vue` to render the configuration UI from the resolved schema.

#### Architectural Flow

1.  **Developer Input:** The user provides their module's gRPC address (e.g., `localhost:8080`) in the Vue UI.
2.  **Frontend to Backend:** The Vue app sends this address to a REST endpoint on the Node.js backend.
3.  **Backend to Module:** The Node.js backend acts as a gRPC-Web proxy. It calls the `GetServiceRegistration` method on the target module using standard gRPC.
4.  **Schema Resolution:** The backend receives the raw OpenAPI schema and uses `@apidevtools/json-schema-ref-parser` to create a single, fully-resolved schema.
5.  **Backend to Frontend:** The resolved schema is sent back to the Vue app.
6.  **UI Rendering:** The Vue app passes the schema to the shared JSON Forms component, which renders the interactive configuration form.

### 3. Component Responsibilities

#### The Developer Tool (The "Free Lunch")

The primary goal of this application is to provide a powerful, optional, standalone testing tool for module developers. By pointing the tool at their gRPC endpoint, a developer gets an instant, interactive test page that shows exactly how their configuration card will render in the main Pipeline Engine.

This tool also serves as the development and hardening environment for the shared form-rendering Vue component.

#### The Shared UI Component

The schema-rendering Vue component, once matured within the developer tool, will be packaged and published as a reusable NPM library.

#### The Pipeline Engine

*   For rendering configuration cards in the main administrative UI, the Engine will consume the shared Vue component library.
*   It will call the `GetServiceRegistration` gRPC endpoint of the target module to retrieve the raw schema.
*   It will then use a schema transformation service (which can be built using the same Node.js logic as the developer tool) to resolve the schema before passing it to the Vue component for rendering.

## Benefits

*   **Simplicity for Module Developers:** The contract remains dead simple, lowering the barrier to entry.
*   **No Engine Dependency for Testing:** Modules can be developed and tested in a completely standalone manner.
*   **Maximum Code Reuse:** The complex UI rendering logic is written once in a shared Vue component and used by both the developer tool and the main engine.
*   **Powerful Tooling:** The developer tool becomes a high-value, optional utility that provides a "free" test UI for developers in any language.
*   **Leverages Modern Web Ecosystem:** Builds on the robust and popular Node.js and Vue ecosystems.
*   **Centralized Control:** The Engine maintains control over the final rendering in the main UI by owning the schema transformation and integration of the shared component.
