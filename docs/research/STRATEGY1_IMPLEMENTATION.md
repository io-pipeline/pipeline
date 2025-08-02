# Strategy 1 (Embedded Object) Implementation

## Overview

This implementation follows **Strategy 1** from the embedding indexing strategy RFC, where all embeddings for a document are stored as nested objects within a single OpenSearch document. This approach provides maximum flexibility for A/B testing and hybrid search capabilities.

## Key Features Implemented

### 1. **Single Document with Nested Embeddings**
- Each `PipeDoc` becomes one OpenSearch document
- All embeddings stored in nested `embeddings` field
- Supports unlimited embedding combinations per document

### 2. **Multi-Dimensional Vector Support**
- Automatic detection of vector dimensions from `PipeDoc`
- Separate nested fields for different dimensions (e.g., `embeddings_384`, `embeddings_768`)
- Prevents OpenSearch mapping conflicts between different vector sizes

### 3. **Deduplication Logic**
- Composite key deduplication: `chunk_config_id + embedding_id + source_text`
- Prevents duplicate embeddings in final document
- Handles multiple processing runs gracefully

### 4. **Primary vs Secondary Embeddings**
- Automatic detection of primary embeddings (title, author, summary)
- `is_primary` flag for targeted search queries
- Supports hybrid search patterns

### 5. **External Versioning**
- Uses `last_modified_date` from `PipeDoc` as external version
- Prevents out-of-order updates
- Leverages OpenSearch's built-in conflict resolution

## Implementation Components

### DocumentConverterService
```java
// Converts PipeDoc to OpenSearchDocument with nested embeddings
List<BulkOperation> prepareBulkOperations(PipeDoc document, String indexName)
```

**Key Features:**
- Single document per `PipeDoc`
- JSON serialization using protobuf standard mapping
- Embedding deduplication and aggregation
- Primary/secondary embedding detection

### SchemaManagerService
```java
// Ensures index exists with proper nested field mappings
Uni<Void> ensureIndexExistsForDocument(PipeDoc document)
```

**Key Features:**
- Distributed locking with Redis
- Dynamic embedding field analysis
- Multi-dimensional vector support
- On-demand index creation

### EmbeddingFieldConfig
```java
// Configuration for embedding fields with different dimensions
public class EmbeddingFieldConfig
```

**Key Features:**
- Dimension-specific field naming
- Run-specific field naming for maximum isolation
- Field name sanitization

## Index Structure

### Base Document Fields
```json
{
  "original_doc_id": "doc-123",
  "doc_type": "article", 
  "created_by": "system",
  "created_at": "2025-01-01T12:00:00Z",
  "last_modified_at": "2025-01-01T12:00:00Z",
  "source_uri": "https://example.com/doc",
  "title": "Document Title",
  "body": "Document body content...",
  "tags": ["keyword1", "keyword2"]
}
```

### Nested Embeddings Structure
```json
{
  "embeddings": [
    {
      "vector": [0.1, 0.2, 0.3, ...],
      "source_text": "Document title",
      "context_text": ["title context"],
      "chunk_config_id": "title_embedding",
      "embedding_id": "minilm_l6",
      "is_primary": true
    },
    {
      "vector": [0.4, 0.5, 0.6, ...],
      "source_text": "First paragraph content...",
      "context_text": ["paragraph context"],
      "chunk_config_id": "body_chunks",
      "embedding_id": "minilm_l6", 
      "is_primary": false
    }
  ]
}
```

### Multi-Dimensional Support
For documents with different vector dimensions:
```json
{
  "embeddings_384": [
    {
      "vector": [384 dimensions],
      "embedding_id": "minilm_l6",
      ...
    }
  ],
  "embeddings_768": [
    {
      "vector": [768 dimensions], 
      "embedding_id": "mpnet_base",
      ...
    }
  ]
}
```

## Search Patterns

### Basic Vector Search
```json
{
  "query": {
    "nested": {
      "path": "embeddings",
      "query": {
        "knn": {
          "embeddings.vector": {
            "vector": [0.1, 0.2, 0.3, ...],
            "k": 10
          }
        }
      },
      "inner_hits": {}
    }
  }
}
```

### Filtered Vector Search (A/B Testing)
```json
{
  "query": {
    "nested": {
      "path": "embeddings",
      "query": {
        "bool": {
          "must": [
            {
              "knn": {
                "embeddings.vector": {
                  "vector": [0.1, 0.2, 0.3, ...],
                  "k": 10
                }
              }
            }
          ],
          "filter": [
            {"term": {"embeddings.embedding_id": "minilm_l6"}},
            {"term": {"embeddings.chunk_config_id": "body_chunks"}}
          ]
        }
      },
      "inner_hits": {}
    }
  }
}
```

### Primary Embeddings Only
```json
{
  "query": {
    "nested": {
      "path": "embeddings",
      "query": {
        "bool": {
          "must": [
            {
              "knn": {
                "embeddings.vector": {
                  "vector": [0.1, 0.2, 0.3, ...],
                  "k": 10
                }
              }
            }
          ],
          "filter": [
            {"term": {"embeddings.is_primary": true}}
          ]
        }
      }
    }
  }
}
```

## Configuration

### Application Properties
```properties
# Index naming
opensearch.default.index-prefix=pipeline
opensearch.default.vector-dimension=384
opensearch.embeddings.field-name=embeddings

# Redis for distributed locking
quarkus.redis.hosts=redis://localhost:6379
```

### Environment Variables
```bash
# OpenSearch connection
OPENSEARCH_HOST=localhost
OPENSEARCH_PORT=9200
OPENSEARCH_USERNAME=admin
OPENSEARCH_PASSWORD=admin

# Redis connection  
REDIS_HOST=localhost
REDIS_PORT=6379
```

## Testing

### Unit Tests
- `DocumentConverterServiceTest`: Basic conversion logic
- `MultiDimensionEmbeddingTest`: Multi-dimensional vector support
- `Strategy1IntegrationTest`: End-to-end Strategy 1 flow

### Integration Tests
- OpenSearch testcontainers for real index operations
- Redis testcontainers for distributed locking
- Full pipeline testing with multiple embedding models

## Benefits of Strategy 1

1. **Flexibility**: Single document contains all embedding variations
2. **A/B Testing**: Easy comparison of different models/chunking strategies  
3. **Hybrid Search**: Combine vector and keyword search in single query
4. **Consistency**: All document data co-located for atomic updates
5. **Performance**: Single document retrieval gets all embeddings
6. **Scalability**: Leverages OpenSearch nested field optimizations

## Limitations

1. **Document Size**: Large documents with many embeddings may hit size limits
2. **Update Complexity**: Updating embeddings requires full document reindex
3. **Memory Usage**: All embeddings loaded together during search
4. **Index Size**: Larger indices due to embedding duplication across models

## Next Steps

1. **Strategy 2 Implementation**: Denormalized multi-index approach
2. **Performance Benchmarking**: Compare strategies with real data
3. **Search API**: High-level search interface for common patterns
4. **Monitoring**: Metrics for embedding field usage and performance
5. **Migration Tools**: Convert between strategies as needed

## Implementation Status

✅ **Completed**
- Single document with nested embeddings
- Multi-dimensional vector support  
- Deduplication logic
- Primary/secondary embedding detection
- External versioning
- Distributed schema management
- Comprehensive test suite

🚧 **In Progress**
- Performance optimization
- Search API development
- Monitoring and metrics

📋 **Planned**
- Strategy 2 implementation
- Cross-strategy benchmarking
- Production deployment guides