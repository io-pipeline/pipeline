# Pipeline Engine Port Allocations

This document defines the comprehensive port allocation strategy for the Pipeline Engine system.

## Port Allocation Strategy

### Base Port Logic
- **Consul Base**: 8500 (default Consul port)
- **Service Base**: 38500 (30000 + 8500, gives us room for 30xxx range)
- **Module Base**: 39000 (one level up from service base)

### Port Ranges
- **Infrastructure**: 8500-8599 (Consul, monitoring)
- **Core Services**: 38500-38599 (registration, management)
- **Processing Modules**: 39000-39099 (echo, parser, embedder, etc.)
- **External Services**: 39100-39199 (OpenSearch, databases)
- **Development Override**: 38000-38099 (dev-only overrides)

## Infrastructure Ports (8500-8599)

| Service | Port | Protocol | Description | Configuration |
|---------|------|----------|-------------|---------------|
| **Consul** | 8500 | HTTP | Consul HTTP API | `consul agent -client=0.0.0.0` |
| Consul | 8501 | HTTPS | Consul HTTPS API (optional) | TLS enabled |
| Consul | 8600 | DNS | Consul DNS | `dig @localhost -p 8600` |
| **Consul gRPC** | 8502 | gRPC | Consul gRPC API | For service mesh |
| **Prometheus** | 8590 | HTTP | Metrics collection | `prometheus.yml` |
| **Grafana** | 8591 | HTTP | Metrics dashboard | `grafana.ini` |
| **Jaeger** | 8592 | HTTP | Distributed tracing | `jaeger-collector` |

## Core Services (38500-38599)

| Service | Port | Protocol | Dev Port | Description | Configuration |
|---------|------|----------|----------|-------------|---------------|
| **Registration Service** | 38501 | HTTP/gRPC | 38001 | Module registry & health | `quarkus.http.port=38501` |
| **Pipeline Engine** | 38502 | HTTP/gRPC | 38002 | Main orchestrator | `quarkus.http.port=38502` |
| **Config Service** | 38503 | HTTP/gRPC | 38003 | Configuration management | `quarkus.http.port=38503` |
| **Gateway/Proxy** | 38510 | HTTP | 38010 | API Gateway | `quarkus.http.port=38510` |
| **Admin Console** | 38520 | HTTP | 38020 | Web management UI | `quarkus.http.port=38520` |

## Processing Modules (39000-39099)

| Module | Port | Protocol | Dev Port | Description | Configuration |
|--------|------|----------|----------|-------------|---------------|
| **echo** | 39000 | HTTP/gRPC | - | Echo/test module | `quarkus.http.port=39000` |
| **parser** | 39001 | HTTP/gRPC | - | Document parsing (Tika) | `quarkus.http.port=39001` |
| **chunker** | 39002 | HTTP/gRPC | - | Text chunking | `quarkus.http.port=39002` |
| **embedder** | 39003 | HTTP/gRPC | - | Vector embedding | `quarkus.http.port=39003` |
| **test-harness** | 39004 | HTTP/gRPC | - | Testing & validation | `quarkus.http.port=39004` |
| **filesystem-crawler** | 39005 | HTTP/gRPC | - | File system connector | `quarkus.http.port=39005` |
| **proxy-module** | 39006 | HTTP/gRPC | - | Dynamic proxy | `quarkus.http.port=39006` |

## External Services (39100-39199)

| Service | Port | Protocol | Description | Configuration |
|---------|------|----------|-------------|---------------|
| **OpenSearch** | 39100 | HTTP | Document search & storage | `opensearch.yml` |
| OpenSearch Dashboard | 39101 | HTTP | OpenSearch UI | `opensearch_dashboards.yml` |
| **PostgreSQL** | 39110 | TCP | Metadata database | `postgresql.conf` |
| **Redis** | 39120 | TCP | Caching & sessions | `redis.conf` |
| **MinIO** | 39130 | HTTP | Object storage | `minio server` |
| MinIO Console | 39131 | HTTP | MinIO management UI | `minio console` |
| **Kafka** | 39140 | TCP | Message streaming | `server.properties` |
| Kafka UI | 39141 | HTTP | Kafka management | `kafka-ui` |

## Development Overrides (38000-38099)

| Service | Port | Description | Usage |
|---------|------|-------------|-------|
| **Registration Service Dev** | 38001 | Development instance | `%dev.quarkus.http.port=38001` |
| **Pipeline Engine Dev** | 38002 | Development instance | `%dev.quarkus.http.port=38002` |
| **Gateway Dev** | 38010 | Development gateway | `%dev.quarkus.http.port=38010` |

## Health Check Endpoints

All services expose health checks on their main port:

| Endpoint | Path | Description |
|----------|------|-------------|
| **Health** | `/health` | Overall health status |
| **Ready** | `/health/ready` | Readiness probe |
| **Live** | `/health/live` | Liveness probe |
| **Metrics** | `/metrics` | Prometheus metrics |

## Service Discovery Configuration

### Registration Service Client Configuration
All modules must configure the registration service client:

```properties
# Registration Service gRPC Client
quarkus.grpc.clients.registration-service.host=localhost
quarkus.grpc.clients.registration-service.port=38501
quarkus.grpc.clients.registration-service.plain-text=true

# Development override
%dev.quarkus.grpc.clients.registration-service.port=38001
```

### Consul Service Discovery
For production, services should use Consul service discovery:

```properties
# Consul configuration
quarkus.stork.registration-service.service-discovery.type=consul
quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:localhost}
quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}
```

## Docker Compose Port Mapping

```yaml
version: '3.8'
services:
  consul:
    ports:
      - "8500:8500"  # HTTP API
      - "8600:8600/udp"  # DNS
  
  registration-service:
    ports:
      - "38501:38501"  # HTTP/gRPC
  
  pipeline-engine:
    ports:
      - "38502:38502"  # HTTP/gRPC
  
  echo:
    ports:
      - "39000:39000"  # HTTP/gRPC
  
  opensearch:
    ports:
      - "39100:9200"   # HTTP API
      - "39101:5601"   # Dashboard
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSUL_HOST` | localhost | Consul server hostname |
| `CONSUL_PORT` | 8500 | Consul HTTP port |
| `REGISTRATION_SERVICE_HOST` | localhost | Registration service host |
| `REGISTRATION_SERVICE_PORT` | 38501 | Registration service port |
| `MODULE_HOST` | localhost | Module hostname for registration |

## Security Considerations

### Production Recommendations
- **TLS**: Enable TLS for all HTTP/gRPC communications in production
- **Authentication**: Implement proper authentication for admin endpoints
- **Firewall**: Restrict port access based on service requirements
- **Consul ACLs**: Enable Consul ACLs for service discovery security

### Development
- **Plain text**: Development uses plain text connections for simplicity
- **Open access**: All ports accessible on localhost for debugging

## Troubleshooting

### Common Port Conflicts
1. **8500**: Often used by other Consul instances
2. **39000**: Check for conflicting development servers
3. **38501**: Verify registration service isn't already running

### Diagnostic Commands
```bash
# Check port usage
netstat -tulpn | grep :8500
lsof -i :38501

# Test connectivity
curl http://localhost:8500/v1/status/leader
curl http://localhost:38501/health

# Service discovery check
curl http://localhost:8500/v1/catalog/services
```

## Migration Guide

### From Legacy Port Scheme
If migrating from a different port allocation:

1. **Update configurations**: Replace old ports with new scheme
2. **Update Docker Compose**: Modify port mappings
3. **Update documentation**: Ensure all references use new ports
4. **Test connectivity**: Verify all services can communicate

### Development to Production
1. **Remove dev overrides**: Remove `%dev.` prefixed properties
2. **Enable TLS**: Configure certificates for HTTPS/gRPC-TLS
3. **Update service discovery**: Switch from direct addresses to Consul
4. **Configure monitoring**: Ensure Prometheus can scrape all services

---

**Note**: This port allocation provides a clear, scalable structure that accommodates growth while maintaining logical organization. The 38xxx/39xxx ranges provide plenty of room for expansion while avoiding common port conflicts.