# Pipeline Engine Deployment Guide

This guide provides step-by-step instructions to recreate the complete Pipeline Engine environment on a new machine.

## Architecture Overview

The Pipeline Engine is a microservices-based data processing system with these components:

1. **Consul** - Service discovery and configuration storage
2. **OpenSearch** - Vector search and document storage
3. **PipeStream Engine** - Central orchestrator (port 38100)
4. **Processing Modules:**
   - Parser - Document parsing (port 39101)
   - Chunker - Text chunking (port 39102) 
   - Embedder - Vector embedding (port 39103)
   - OpenSearch Sink - Vector storage (port 39104)
5. **Registration Service** - Module registry (port 38001)

## Prerequisites

### Required Software
- **Java 21** (OpenJDK or equivalent)
- **Docker & Docker Compose** (for Consul and OpenSearch)
- **Gradle** (or use included gradlew)
- **Git** (to clone the repository)

### Recommended System Requirements
- **RAM:** 8GB minimum, 16GB recommended
- **CPU:** 4 cores minimum
- **Disk:** 20GB free space
- **Network:** Ports 8500, 9200, 38001, 38100, 39101-39104 available

## Step 1: Clone and Build Project

```bash
# Clone the repository
git clone <repository-url>
cd first_try_registration_service

# Build all modules
./gradlew clean build

# Verify build successful
echo "Build completed successfully"
```

## Step 2: Infrastructure Setup

### Start Consul (Service Discovery)
```bash
# Start Consul in Docker
docker run -d \
  --name consul \
  -p 8500:8500 \
  -p 8600:8600/udp \
  hashicorp/consul:1.21 \
  agent -dev -ui -client=0.0.0.0

# Verify Consul is running
curl http://localhost:8500/v1/status/leader
```

### Start OpenSearch (Vector Database)
```bash
# Create OpenSearch environment file
cat > opensearch/.env << 'EOF'
OPENSEARCH_USERNAME=admin
OPENSEARCH_PASSWORD=admin
EOF

# Start OpenSearch cluster
cd opensearch
docker-compose up -d

# Verify OpenSearch is running
curl -u admin:admin -k http://localhost:9200/_cluster/health
cd ..
```

## Step 3: Service Startup Order

**IMPORTANT:** Start services in this exact order for proper registration:

### 1. Registration Service (38001)
```bash
# Terminal 1
./gradlew :applications:registration-service:quarkusDev
```
Wait for: "registration-service started on http://localhost:38001"

### 2. Processing Modules

#### Parser Module (39101)
```bash
# Terminal 2  
./gradlew :modules:parser:quarkusDev
```

#### Chunker Module (39102)  
```bash
# Terminal 3
./gradlew :modules:chunker:quarkusDev
```

#### Embedder Module (39103)
```bash
# Terminal 4
./gradlew :modules:embedder:quarkusDev
```

#### OpenSearch Sink Module (39104)
```bash
# Terminal 5
export OPENSEARCH_USERNAME=admin
export OPENSEARCH_PASSWORD=admin
./gradlew :modules:opensearch-sink:quarkusDev
```

### 3. PipeStream Engine (38100)
```bash
# Terminal 6
./gradlew :applications:pipestream-engine:quarkusDev
```

## Step 4: Verification

### Check Service Registration
```bash
# All services should be registered in Consul
curl http://localhost:8500/v1/catalog/services | jq .

# Expected services: consul, registration-service, pipestream-engine, parser, chunker, embedder, opensearch
```

### Check Pipeline Configuration
```bash
# Verify test pipeline exists
curl -s "http://localhost:8500/v1/kv/pipeline/clusters/dev/pipelines/test-pipeline/config" | jq -r '.[0].Value' | base64 -d | jq .

# Should show: parse-docs -> chunk-text -> embed-chunks -> store-vectors
```

### Test End-to-End Pipeline
```bash
# Test document processing through complete pipeline
curl -X POST -H "Content-Type: application/json" \
  -d '{
    "connector_type": "test-connector",
    "connector_id": "test-connector-1", 
    "document": {
      "content": "This is a test document for pipeline processing",
      "source_url": "test://document",
      "metadata": {"test": "true"}
    }
  }' \
  http://localhost:38100/processConnectorDoc

# Should return: {"accepted": true, "stream_id": "...", "message": "..."}
```

### Verify OpenSearch Indexing
```bash
# Check if document was indexed
curl -u admin:admin "http://localhost:9200/documents-*/_search?pretty"

# Should show indexed documents with embeddings
```

## Configuration Files

### Key Environment Variables
```bash
# OpenSearch Authentication
export OPENSEARCH_USERNAME=admin
export OPENSEARCH_PASSWORD=admin

# Consul Connection (optional, defaults work)
export CONSUL_HOST=localhost
export CONSUL_PORT=8500
```

### Important Configuration Properties

**applications/pipestream-engine/src/main/resources/application.properties:**
```properties
pipeline.consul.kv-prefix=pipeline
quarkus.http.port=38100
```

**modules/opensearch-sink/src/main/resources/application.properties:**
```properties
opensearch.hosts=localhost:9200
opensearch.protocol=http
opensearch.default.vector-space-type=cosine
```

## Troubleshooting

### Common Issues

1. **"Service not found" errors:**
   - Check Consul UI: http://localhost:8500/ui
   - Verify all services are registered and healthy

2. **"UNAVAILABLE: io exception":**
   - Ensure services started in correct order
   - Check that Consul is running first

3. **OpenSearch connection errors:**
   - Verify OPENSEARCH_USERNAME and OPENSEARCH_PASSWORD are set
   - Check OpenSearch is running: `curl -u admin:admin http://localhost:9200/`

4. **Pipeline step not found:**
   - Recreate test pipeline: `curl -X POST http://localhost:38100/api/test/routing/create-test-pipeline`
   - Check pipeline exists in Consul

### Debug Commands
```bash
# Check service health
curl http://localhost:8500/v1/health/service/pipestream-engine
curl http://localhost:8500/v1/health/service/parser
curl http://localhost:8500/v1/health/service/chunker  
curl http://localhost:8500/v1/health/service/embedder
curl http://localhost:8500/v1/health/service/opensearch

# Test pipeline step configuration
curl http://localhost:38100/api/test/routing/step-config/dev/test-pipeline/parse-docs
```

## Production Deployment

For production deployment:

1. **Replace Consul dev mode** with production cluster
2. **Configure OpenSearch security** properly
3. **Use proper service discovery** instead of localhost
4. **Set up monitoring** and logging
5. **Configure resource limits** and scaling

## Service Dependencies

```
Consul (8500) 
├── Registration Service (38001)
├── Parser Module (39101)
├── Chunker Module (39102) 
├── Embedder Module (39103)
├── OpenSearch Sink (39104)
└── PipeStream Engine (38100)

OpenSearch (9200)
└── OpenSearch Sink Module
```

## Support

If you encounter issues:

1. Check all services are running and registered in Consul
2. Verify OpenSearch credentials and connectivity
3. Ensure pipeline configuration exists in Consul
4. Test individual components before end-to-end flow