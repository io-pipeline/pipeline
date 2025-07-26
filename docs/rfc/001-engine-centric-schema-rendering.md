
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

This proposal shifts the responsibility of schema transformation from individual modules to the central Pipeline Engine and a new, reusable library.

### 1. The Module Developer Contract (The "Golden Rule")

The contract for a module developer, regardless of language, is simplified to its absolute minimum:

*   A module MUST implement the `PipeStepProcessor` gRPC service.
*   Its `GetServiceRegistration` method MUST return a `ServiceRegistrationResponse`.
*   The `json_config_schema` field within this response MUST contain the module's complete, standard, **unresolved OpenAPI 3.1 schema**.

This is the **only** schema-related requirement for a module.

### 2. The New Shared Library: `pipeline-schema-utils`

A new, reusable Java library will be created to encapsulate all schema transformation logic.

*   **Name:** `io.pipeline:pipeline-schema-utils`
*   **Framework:** It will be a Quarkus-aware library to seamlessly integrate with our existing tools, tests, and dependency injection framework.
*   **Core API:** It will provide a `SchemaTransformer` service with two primary methods:
    *   `public String toRenderingSchema(String rawOpenApiSchema)`: Resolves all `$ref`s and prepares the schema for JSONForms.
    *   `public String toValidationSchema(String rawOpenApiSchema)`: Cleans the schema for JSON Schema v7 validation.

### 3. Component Responsibilities & Architectural Flow

#### For Our Internal Java Modules (e.g., Parser, Chunker)

*   Our Java-based modules will include `pipeline-schema-utils` as a dependency.
*   They will expose a local REST endpoint (e.g., `/ui/rendering-schema`) for their self-contained test pages.
*   This endpoint will use the injected `SchemaTransformer` to process its own raw schema and serve the render-ready version to its local UI. This ensures our modules are self-contained and independently testable.

#### For External/Non-Java Modules

*   External developers are **not required** to use the `proxy-module`. They can choose to implement everything themselves if they wish.
*   However, we will provide the `proxy-module` as a powerful, **optional, standalone testing tool**.

#### The `proxy-module` (The "Free Lunch")

*   The `proxy-module` will be a standalone Quarkus application that includes the `pipeline-schema-utils` library.
*   A non-Java developer can run it and point it at their module's gRPC endpoint.
*   The proxy will automatically:
    1.  Proxy all standard `PipeStepProcessor` calls to their module.
    2.  Wrap their module with Quarkus features like metrics, logging, and tracing.
    3.  Expose a local web server with a test page.
    4.  This test page will use a REST endpoint on the proxy (`/ui/rendering-schema`) which, in turn, uses the `SchemaTransformer` to provide a fully rendered, interactive configuration form for the non-Java module.

This provides an immediate, zero-effort way for external developers to test their module and see exactly how its configuration card will render in the main Pipeline Engine UI.

#### The Pipeline Engine

*   For rendering configuration cards in the main administrative UI, the Engine will call the `GetServiceRegistration` gRPC endpoint of the target module.
*   It will retrieve the raw, unresolved OpenAPI 3.1 schema from the `json_config_schema` field.
*   It will use its own instance of the `SchemaTransformer` (from the shared `pipeline-schema-utils` library) to convert the raw schema into the final rendering schema required by the `UniversalConfigCard`.

## Benefits

*   **Simplicity for Module Developers:** The contract is dead simple, lowering the barrier to entry for creating new modules.
*   **No Engine Dependency for Testing:** Modules can be developed and tested in a completely standalone manner.
*   **Maximum Code Reuse:** The complex transformation logic is written once in a shared, Quarkus-aware library and used by all our Java components.
*   **Powerful Tooling:** The `proxy-module` becomes a high-value, optional tool that provides a "free" test UI and other enterprise-grade features to developers in any language.
*   **Centralized Control:** The Engine maintains control over the final rendering in the main UI, ensuring consistency and allowing for future enhancements.
