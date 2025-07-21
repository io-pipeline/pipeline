#!/bin/bash

# Start OpenSearch cluster and seed it with sample data
# Requires OPENSEARCH_INITIAL_ADMIN_PASSWORD environment variable

if [ -z "$OPENSEARCH_INITIAL_ADMIN_PASSWORD" ]; then
    echo "❌ Error: OPENSEARCH_INITIAL_ADMIN_PASSWORD environment variable is required"
    echo "   Set it with: export OPENSEARCH_INITIAL_ADMIN_PASSWORD=p1p31in3eyeoh"
    exit 1
fi

echo "🚀 Starting OpenSearch cluster..."
echo "📊 Admin credentials: admin / $OPENSEARCH_INITIAL_ADMIN_PASSWORD"

# Start the cluster
docker compose up -d

# Wait for OpenSearch to be ready
echo "⏳ Waiting for OpenSearch to start..."
until curl -k -u admin:$OPENSEARCH_INITIAL_ADMIN_PASSWORD https://localhost:9200/_cluster/health > /dev/null 2>&1; do
    echo "   Still starting..."
    sleep 5
done

echo "✅ OpenSearch is ready!"
echo "🌐 OpenSearch API: https://localhost:9200"
echo "📊 OpenSearch Dashboards: http://localhost:5601"
echo ""
echo "🔧 Seeding sample vector indices..."

# Create sample vector index for document chunks
curl -k -X PUT "https://localhost:9200/documents" \
     -u admin:$OPENSEARCH_INITIAL_ADMIN_PASSWORD \
     -H 'Content-Type: application/json' \
     -d '{
  "mappings": {
    "properties": {
      "document_id": { "type": "keyword" },
      "chunk_id": { "type": "keyword" },
      "chunk_text": { "type": "text" },
      "chunk_index": { "type": "integer" },
      "embedding": {
        "type": "knn_vector",
        "dimension": 384,
        "method": {
          "name": "hnsw",
          "space_type": "cosinesimilarity",
          "engine": "lucene"
        }
      },
      "metadata": {
        "properties": {
          "source": { "type": "keyword" },
          "title": { "type": "text" },
          "document_type": { "type": "keyword" },
          "connector_type": { "type": "keyword" },
          "processing_timestamp": { "type": "date" }
        }
      }
    }
  },
  "settings": {
    "index": {
      "knn": true,
      "knn.algo_param.ef_search": 100
    }
  }
}'

echo ""
echo "✅ OpenSearch cluster ready for vector indexing!"
echo "   Sample 'documents' index created with 384-dimensional vectors"
