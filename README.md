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

## 🎨 Vue.js Dashboard Setup

The Chunker module includes a modern Vue.js dashboard with OpenAPI-driven forms. Follow these steps to set it up on a new machine:

### Prerequisites for Vue.js Development
- **Node.js 18+** and **npm**
- All Java prerequisites (above)

### Quick Setup
```bash
# 1. Install Node.js dependencies
cd modules/chunker/src/main/ui-vue
npm install

# 2. Build the project (includes Vue.js dashboard)
cd ../../../../  # Back to project root
./gradlew :modules:chunker:build

# 3. Start the chunker service
./gradlew :modules:chunker:quarkusDev
```

### Vue.js Dashboard Features
- **Config Card**: Schema-driven configuration forms using JSON Schema Forms
- **Demo Documents**: Interactive document browser with 5 sample documents
- **Metadata Dashboard**: Real-time performance metrics and system monitoring

### Development Mode
For Vue.js frontend development with hot reload:
```bash
# Terminal 1: Start Quarkus backend
./gradlew :modules:chunker:quarkusDev

# Terminal 2: Start Vue.js dev server (optional - for hot reload)
cd modules/chunker/src/main/ui-vue
npm run dev
```

### Access Points
- **Vue.js Dashboard**: http://localhost:39102/ (embedded in Quarkus)
- **REST API**: http://localhost:39102/api/chunker/service/
- **OpenAPI Docs**: http://localhost:39102/q/swagger-ui/

### Vue.js Technology Stack
- **Vue.js 3** with Composition API
- **JSON Schema Forms** for dynamic form generation
- **Vite** for fast development and building
- **Axios** for REST API integration
- **Quinoa** for Quarkus integration

### Build Integration
The Vue.js build is automatically integrated with Gradle via Quinoa:
- `npm install` runs automatically during Gradle build
- `npm run build` generates production assets
- Vue.js assets are served by Quarkus at runtime
- No separate deployment needed

## 📦 What's Included

This deployment package includes:
- ✅ Complete source code
- ✅ Automated build and deployment scripts
- ✅ Infrastructure setup (Consul + OpenSearch)
- ✅ Service discovery and registration
- ✅ End-to-end testing and validation
- ✅ Comprehensive documentation
- ✅ Production-ready configuration examples
- ✅ Modern Vue.js dashboard with OpenAPI integration