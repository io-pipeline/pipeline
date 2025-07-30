# PipeDoc Repository Service

A Quarkus-based gRPC service providing CRUD operations for PipeDoc and ModuleProcessRequest storage using MongoDB.

## Features

- **MongoDB with Panache**: Simplified database operations using the active record pattern
- **gRPC Service**: Full implementation of the PipeDocRepository service defined in `pipedoc_repository.proto`
- **Dev Services**: Automatic MongoDB container provisioning in development mode
- **Health Checks**: Built-in health endpoints for monitoring
- **Quinoa Integration**: Ready for Node.js frontend integration

## Running in Development

```bash
# From the project root directory
./gradlew :applications:pipedoc-repository-service:quarkusDev
```

The service will start with:
- HTTP endpoint: http://localhost:38002
- gRPC endpoint: localhost:38003
- MongoDB: Automatically provisioned by Dev Services

## Service Endpoints

### gRPC Operations

- `CreatePipeDoc`: Store a new PipeDoc with metadata
- `GetPipeDoc`: Retrieve a PipeDoc by storage ID
- `UpdatePipeDoc`: Update an existing PipeDoc
- `DeletePipeDoc`: Delete a PipeDoc
- `ListPipeDocs`: List PipeDocs with filtering and pagination
- `BatchCreatePipeDocs`: Stream multiple PipeDocs for batch creation
- `SaveProcessRequest`: Store a ModuleProcessRequest for testing
- `GetProcessRequest`: Retrieve a stored ModuleProcessRequest
- `ListProcessRequests`: List stored process requests
- `DeleteProcessRequest`: Delete a process request
- `ExportPipeDocs`: Export documents in various formats
- `ImportPipeDocs`: Import documents from external sources

### Health Check

- Health endpoint: http://localhost:38002/q/health
- Liveness probe: http://localhost:38002/q/health/live
- Readiness probe: http://localhost:38002/q/health/ready

## MongoDB Collections

- `pipe_docs`: Stores PipeDoc entities with metadata
- `process_requests`: Stores ModuleProcessRequest test cases

## Configuration

Key configuration properties in `application.properties`:

```properties
# MongoDB database name
quarkus.mongodb.database=pipedoc-repository

# Dev Services auto-configuration
%dev.quarkus.mongodb.devservices.enabled=true

# gRPC configuration
quarkus.grpc.server.port=38003
quarkus.grpc.server.enable-reflection-service=true
```

## Building

```bash
# Build from project root
./gradlew :applications:pipedoc-repository-service:build

# Build native image (requires GraalVM)
./gradlew :applications:pipedoc-repository-service:build -Dquarkus.native.enabled=true
```

## Integration with Dev Tools

This service is designed to work with the Pipeline Developer Tools, sharing the same MongoDB instance when both are running locally.

### Architecture

- **Desktop Dev Tools (Node.js)**: Connects via gRPC only (port 38003)
- **Quinoa Frontend**: Uses REST endpoints (port 38002) for future web UI
- **Shared MongoDB**: Both services use the same MongoDB instance on port 27017

### Running with Dev Tools

1. Start this repository service: `./gradlew :applications:pipedoc-repository-service:quarkusDev`
   - Quarkus Dev Services will automatically start a MongoDB container labeled `pipeline-mongodb`
2. Start the Node.js dev tools
   - Configure it to connect to `mongodb://localhost:27017`
3. Both will use the same MongoDB instance and database (`pipeline-dev-tools`)

### MongoDB Dev Services

The service uses Quarkus Dev Services with:
- Service name: `pipeline-mongodb` (shared between all services)
- Port: 27017 (standard MongoDB port)
- Database: `pipeline-dev-tools`

This means:
- First service to start creates the MongoDB container
- Subsequent services reuse the same container
- Container is labeled with `quarkus-dev-service-mongodb` and service name `pipeline-mongodb`