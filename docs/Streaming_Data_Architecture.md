# Streaming Data Architecture: A Self-Describing Flow

## Core Principle: Data Carries Its Own State

This document outlines the high-throughput, stateless streaming architecture used in the pipeline. The core principle is that data packets, referred to as `PipeDoc`s, are designed to be **self-describing**. As a `PipeDoc` flows through the pipeline, each service adds its "imprint" to the document's metadata.

This design eliminates the need for intermediate state-lookup calls (e.g., to a database or a central registry) during the pipeline execution. The state is the data itself. This results in a highly efficient, loosely coupled, and scalable system.

## End-to-End Data Flow

Here is a step-by-step breakdown of the journey of a single logical document through the pipeline.

### 1. Origin

A raw document enters the pipeline. It has a unique identifier and some content that needs to be processed.

*   **Example `PipeDoc`:**
    ```json
    {
      "doc_id": "doc_123",
      "doc_type": "article",
      "source_data": {
        "title": "The Future of AI",
        "text": "A long body of text about artificial intelligence..."
      }
    }
    ```

### 2. The Chunker Service

The first processing stage is the `chunker`. Its job is to break down large pieces of content into smaller, manageable chunks suitable for embedding.

*   **Action:** The `chunker` receives the raw document. It is configured with a specific chunking strategy (e.g., `sentences_512_10`, meaning chunk by sentences, with a max size of 512 tokens and an overlap of 10).
*   **Crucial Imprint:** For each chunk it creates, it generates a new `PipeDoc` that contains the chunked text and adds its processing lineage to the metadata.
*   **Example Output Stream (one `PipeDoc` per chunk):**
    ```json
    {
      "original_doc_id": "doc_123",
      "doc_type": "article",
      "source_field": "text",
      "chunk_config_id": "sentences_512_10",
      "chunked_text": "A long body of text about..."
    }
    ```
    ```json
    {
      "original_doc_id": "doc_123",
      "doc_type": "article",
      "source_field": "text",
      "chunk_config_id": "sentences_512_10",
      "chunked_text": "...of text about artificial intelligence..."
    }
    ```

### 3. The Embedder Service

The `embedder` receives the stream of chunked `PipeDoc`s and generates vector embeddings.

*   **Action:** The `embedder` is configured with a specific embedding model (e.g., `minilm_l6_v2`). For each `PipeDoc` it receives, it generates a vector from the `chunked_text`.
*   **Crucial Imprint:** It preserves all the metadata it received from the `chunker` and adds its own imprint: the `embedding_config_id` and the resulting `vector`.
*   **Example Output Stream (one `PipeDoc` per embedding):**
    ```json
    {
      "original_doc_id": "doc_123",
      "doc_type": "article",
      "source_field": "text",
      "chunk_config_id": "sentences_512_10",
      "embedding_config_id": "minilm_l6_v2",
      "vector": [0.12, -0.45, 0.89, ...]
    }
    ```

### 4. The OpenSearch Sink Service

The final stage, the `opensearch-sink`, receives these fully processed, self-describing `PipeDoc`s and indexes them.

*   **Action:** For each `PipeDoc`, the sink has all the information it needs without making any external service calls.
    *   The `vector` to be indexed.
    *   The complete "naming convention" context: `doc_type`, `source_field`, `embedding_config_id`.
*   **Result:** The sink uses its internal `IndexingStrategy` configuration to dynamically construct the target index name (e.g., `pipeline-article-text-minilm_l6_v2`). It then checks its local cache, creates the index with the correct vector mapping if it's the first time seeing this combination, and sends the document to OpenSearch for indexing.

## Architectural Advantages

This "self-describing data" approach provides several key benefits:

*   **High Throughput:** The pipeline is non-blocking. There are no synchronous network calls to external services for state lookups, allowing data to flow continuously at high speed.
*   **Stateless Services:** The `chunker` and `embedder` services are completely stateless. They do not need to store any information about the documents they process, making them easy to scale horizontally, deploy, and maintain.
*   **Loose Coupling:** Services are decoupled. They only need to understand the `PipeDoc` format and their immediate upstream/downstream neighbors. This allows for individual services to be updated, replaced, or tested independently without affecting the entire system.
*   **Resilience & Debuggability:** If a single `PipeDoc` is malformed or causes an error, it can be easily isolated and routed to a dead-letter queue for later analysis without halting the entire pipeline. The rich metadata within the failed `PipeDoc` provides a complete history of its processing, making debugging significantly easier.
