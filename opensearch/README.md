# OpenSearch Configuration

This directory contains the Docker Compose setup for OpenSearch cluster used by the Pipeline Engine.

## Quick Start

```bash
# Create environment file
cat > .env << 'EOF'
OPENSEARCH_USERNAME=admin
OPENSEARCH_PASSWORD=admin
EOF

# Start OpenSearch cluster
docker-compose up -d

# Verify cluster is running
curl -u admin:admin http://localhost:9200/_cluster/health
```

## Configuration Details

### Security Settings
- **Username:** admin  
- **Password:** admin
- **Protocol:** HTTP (not HTTPS for development)
- **Authentication:** Basic Auth
- **SSL:** Disabled for development ease

### Cluster Configuration
- **Single node cluster** for development
- **Port:** 9200 (HTTP)
- **JVM Heap:** 512MB (suitable for development)
- **Plugins:** Required plugins are installed automatically

### Vector Search Configuration
The OpenSearch cluster is configured for vector search with:
- **KNN Plugin:** Enabled for vector similarity search
- **Vector Space Type:** Cosine similarity (configured in application)
- **Vector Dimensions:** 384 (default, configurable per index)
- **Algorithm:** HNSW for efficient approximate nearest neighbor search

## Index Management

The Pipeline Engine automatically creates indices with this naming pattern:
```
documents-{chunking_type}_{chunk_size}_{overlap}
```

Example indices:
- `documents-chunk-text_chunks_token-body-500-50`
- `documents-paragraph_chunks_500_50`

### Dynamic Schema Creation
Each index is created with:
- **Vector field:** `embedding` (384 dimensions by default)
- **Content fields:** Dynamic mapping for document content
- **Metadata fields:** Structured metadata storage
- **Nested vectors:** Support for multiple embeddings per document

## Monitoring

### Health Check
```bash
curl -u admin:admin http://localhost:9200/_cluster/health?pretty
```

### Index Status
```bash
curl -u admin:admin http://localhost:9200/_cat/indices?v
```

### Search Documents
```bash
curl -u admin:admin "http://localhost:9200/documents-*/_search?pretty"
```

### Vector Search Example
```bash
curl -u admin:admin -X POST "http://localhost:9200/documents-*/_search" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "knn": {
        "embedding": {
          "vector": [0.1, 0.2, 0.3, ...],
          "k": 5
        }
      }
    }
  }'
```

## Production Considerations

For production deployment:

1. **Enable SSL/TLS**
   ```yaml
   environment:
     - plugins.security.ssl.http.enabled=true
     - plugins.security.ssl.transport.enabled=true
   ```

2. **Configure proper authentication**
   - Use strong passwords
   - Configure role-based access control
   - Set up certificate-based authentication

3. **Scale the cluster**
   - Add multiple nodes for high availability
   - Configure proper replication settings
   - Set up cross-cluster replication if needed

4. **Resource allocation**
   - Increase JVM heap size based on data volume
   - Configure appropriate disk space
   - Set up monitoring and alerting

5. **Network security**
   - Use private networks
   - Configure firewall rules
   - Enable audit logging

## Troubleshooting

### Common Issues

1. **Connection refused**
   - Check if Docker is running
   - Verify port 9200 is not occupied
   - Check Docker logs: `docker-compose logs opensearch`

2. **Authentication failed**
   - Verify username/password in .env file
   - Check if security plugin is properly configured
   - Ensure environment variables are loaded

3. **Low disk space**
   - OpenSearch requires sufficient disk space
   - Check available space: `df -h`
   - Clean up old indices if needed

4. **Memory issues**
   - Increase Docker memory limits
   - Adjust JVM heap size in docker-compose.yml
   - Monitor memory usage

### Debug Commands
```bash
# Check container status
docker-compose ps

# View logs
docker-compose logs -f opensearch

# Check cluster settings
curl -u admin:admin "http://localhost:9200/_cluster/settings?pretty"

# List all indices
curl -u admin:admin "http://localhost:9200/_cat/indices?v&s=index"

# Check node info
curl -u admin:admin "http://localhost:9200/_nodes?pretty"
```

## Data Persistence

Document data is persisted in Docker volumes. To completely reset:

```bash
# Stop and remove everything including volumes
docker-compose down -v

# Start fresh
docker-compose up -d
```

⚠️ **Warning:** The `-v` flag will delete all stored documents and indices.