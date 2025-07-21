# OpenSearch Installation and Setup

This guide covers setting up the OpenSearch cluster for vector indexing in the pipeline system.

## Prerequisites

- Docker and Docker Compose
- curl (for testing)

## Setup Steps

### 1. Set Environment Variable

```bash
export OPENSEARCH_INITIAL_ADMIN_PASSWORD=p1p31in3eyeoh
```

### 2. Start OpenSearch Cluster

```bash
cd opensearch
chmod +x start-opensearch.sh
./start-opensearch.sh
```

This will:
- Start a 2-node OpenSearch cluster with OpenSearch Dashboards
- Wait for the cluster to be ready
- Create a sample vector index for document chunks

## Access Points

- **OpenSearch API**: https://localhost:9200
- **OpenSearch Dashboards**: http://localhost:5601
- **Admin Credentials**: admin / p1p31in3eyeoh

## Sample Vector Index

The startup script creates a `documents` index with:
- **384-dimensional vectors** (matching ALL_MINILM_L6_V2 embeddings)
- **Document chunking support** (chunk_id, chunk_index, chunk_text)
- **Metadata fields** (source, title, document_type, connector_type)
- **KNN search optimization** (HNSW algorithm with cosine similarity)

## Testing the Setup

### Check cluster health:
```bash
curl -k -u admin:p1p31in3eyeoh https://localhost:9200/_cluster/health
```

### List indices:
```bash
curl -k -u admin:p1p31in3eyeoh https://localhost:9200/_cat/indices
```

### Search vectors (after data is indexed):
```bash
curl -k -u admin:p1p31in3eyeoh https://localhost:9200/documents/_search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "knn": {
        "embedding": {
          "vector": [0.1, 0.2, ...],
          "k": 10
        }
      }
    }
  }'
```

## Dynamic Schema Creation

The opensearch-sink module will automatically:
1. **Inspect embedded documents** to determine vector dimensions
2. **Create new indices** if they don't exist 
3. **Store schema metadata** in Consul for reuse
4. **Index document chunks** with their embeddings

This enables truly dynamic vector indexing for any document type and embedding model.