# Pipeline Engine - Microservices Data Processing System

A complete microservices-based data processing pipeline with service discovery, vector embeddings, and OpenSearch storage.

## 🚀 Quick Start (New Machine Deployment)

### Prerequisites
- **Java 21** (OpenJDK or equivalent)
- **Docker & Docker Compose**
- **Git**

### One-Command Deployment
```bash
git clone <repository-url>
cd first_try_registration_service
./scripts/quick-start.sh
```

### Manual Deployment
```bash
# 1. Build project
./gradlew clean build

# 2. Start infrastructure
./scripts/start-infrastructure.sh

# 3. Start services  
./scripts/start-services.sh

# 4. Test deployment
./scripts/test-pipeline.sh
```

## 🏗️ Architecture

- **Consul** (8500) - Service discovery & configuration
- **OpenSearch** (9200) - Vector database & search
- **PipeStream Engine** (38100) - Central orchestrator
- **Processing Modules:**
  - Parser (39101) - Document parsing
  - Chunker (39102) - Text chunking  
  - Embedder (39103) - Vector embeddings
  - OpenSearch Sink (39104) - Vector storage

## 📖 Documentation

- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Comprehensive deployment guide
- **[opensearch/README.md](opensearch/README.md)** - OpenSearch configuration
- **Service APIs** - Available at `http://localhost:{port}/swagger-ui`

## 🛠️ Management Commands

```bash
# Start everything
./scripts/quick-start.sh

# Stop everything  
./scripts/stop-services.sh

# Test deployment
./scripts/test-pipeline.sh

# View logs
ls -la logs/
```

## ✅ Validation

After deployment, verify:
- Consul UI: http://localhost:8500/ui
- PipeStream Engine: http://localhost:38100/swagger-ui
- All services registered and healthy
- Test pipeline processes documents end-to-end

## 🎯 Pipeline Flow

Document → Parser → Chunker → Embedder → OpenSearch Sink

Each step is processed by separate microservices with automatic service discovery and load balancing.

## 🔧 Configuration

Key configuration files:
- `applications/pipestream-engine/src/main/resources/application.properties`
- `modules/opensearch-sink/src/main/resources/application.properties`
- `opensearch/.env` (OpenSearch credentials)

## 📦 What's Included

This deployment package includes:
- ✅ Complete source code
- ✅ Automated build and deployment scripts
- ✅ Infrastructure setup (Consul + OpenSearch)
- ✅ Service discovery and registration
- ✅ End-to-end testing and validation
- ✅ Comprehensive documentation
- ✅ Production-ready configuration examples