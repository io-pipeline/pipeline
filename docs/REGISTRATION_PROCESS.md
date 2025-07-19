# Registration Process Documentation

## Overview

This document explains how services register themselves with Consul in the Pipeline Engine system, covering both the registration service's self-registration and module registration via annotations.

## Architecture

### Two Registration Mechanisms

1. **Registration Service Self-Registration**: `RegistrationServiceSelfRegistration`
   - The registration service registers itself directly with Consul
   - Uses direct Consul client API calls
   - Required for bootstrap - other services discover the registration service through Consul

2. **Module Auto-Registration**: `PipelineAutoRegistrationBean` + `@PipelineAutoRegister`
   - Modules annotated with `@PipelineAutoRegister` auto-register via gRPC calls
   - Uses Stork service discovery to find the registration service
   - Consul-only approach - no direct host/port fallbacks

## Registration Service Self-Registration

### Class: `RegistrationServiceSelfRegistration`

**Purpose**: Bootstrap registration - registers the registration service with Consul so other services can discover it.

**Key Configuration**:
```java
@ConfigProperty(name = "quarkus.profile")
String profile;

@ConfigProperty(name = "quarkus.application.name", defaultValue = "registration-service") 
String applicationName;

@ConfigProperty(name = "quarkus.http.port", defaultValue = "39100")
int httpPort;
```

**Service Registration Details**:
- **Service ID**: `{applicationName}-{hostname}-{httpPort}` (e.g., `registration-service-localhost-38001`)
- **Service Name**: `{applicationName}` (e.g., `registration-service`)
- **Address**: Hostname determined by profile:
  - `dev` profile → `localhost`
  - Production → Environment variables or container hostname
- **Port**: HTTP port (unified server mode)
- **Tags**: `["grpc", "registration", "core-service", "version:{version}"]`

**Health Check Configuration**:
```java
CheckOptions checkOptions = new CheckOptions()
    .setName("Registration Service Health Check")
    .setGrpc(hostname + ":" + httpPort)     // gRPC health check
    .setInterval("10s")
    .setDeregisterAfter("1m");              // Consul minimum requirement
```

**Critical Settings**:
- `module.auto-register.enabled=false` - Disables annotation-based registration for the registration service
- Profile detection uses `@ConfigProperty(name = "quarkus.profile")` - the Quarkus way
- gRPC health checks for gRPC services (not HTTP)

## Module Auto-Registration

### Class: `PipelineAutoRegistrationBean`

**Purpose**: Automatically registers modules annotated with `@PipelineAutoRegister` by making gRPC calls to the registration service.

**Discovery Mechanism**: 
- Uses Stork service discovery to find `registration-service`
- Pure Consul approach - no direct host/port fallbacks
- Fails fast if Consul is not available

**Required Configuration**:
```properties
# gRPC Client Configuration with Stork
quarkus.grpc.clients.registration-service.host=registration-service
quarkus.grpc.clients.registration-service.name-resolver=stork

# Stork Service Discovery Configuration  
quarkus.stork.registration-service.service-discovery.type=consul
quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:localhost}
quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}
```

**Critical Configuration**: The `host=registration-service` property tells the gRPC client what service name to resolve via Stork.

### Annotation: `@PipelineAutoRegister`

**Usage**:
```java
@GrpcService
@PipelineAutoRegister(
    moduleType = "processor",
    useHttpPort = true,
    metadata = {"category=data-processing"}
)
public class EchoServiceImpl implements PipeStepProcessor {
    // Implementation
}
```

**Registration Flow**:
1. `PipelineAutoRegistrationBean` scans for `@PipelineAutoRegister` annotated beans
2. Uses Stork to discover the registration service in Consul
3. Makes gRPC call to registration service with module details
4. Registration service registers the module in Consul

## Configuration Patterns

### Registration Service (`application.properties`)
```properties
quarkus.application.name=registration-service

# Port allocation - bind to all interfaces for 2-way connectivity
quarkus.http.port=38501
quarkus.http.host=0.0.0.0
%dev.quarkus.http.port=38001
%dev.quarkus.http.host=0.0.0.0

# Disable auto-registration (uses self-registration instead)
module.auto-register.enabled=false

# Consul service registration
quarkus.stork.registration-service.service-registrar.type=consul
quarkus.stork.registration-service.service-registrar.consul-host=${CONSUL_HOST:consul}
%dev.quarkus.stork.registration-service.service-registrar.consul-host=localhost
```

### Module Configuration (e.g., `echo/application.properties`)
```properties
# Module identification
module.name=echo
quarkus.application.name=echo

# Port allocation - bind to all interfaces for 2-way connectivity
quarkus.http.port=39000
quarkus.http.host=0.0.0.0
%dev.quarkus.http.port=39100
%dev.quarkus.http.host=0.0.0.0

# Registration Service Discovery via Consul
quarkus.grpc.clients.registration-service.host=registration-service
quarkus.grpc.clients.registration-service.name-resolver=stork
quarkus.stork.registration-service.service-discovery.type=consul
quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:consul}
%dev.quarkus.stork.registration-service.service-discovery.consul-host=localhost

# Module registration configuration - control advertisement address
module.registration.host=localhost
```

## Startup Sequence

### Correct Order
1. **Start Consul**: `consul agent -dev -log-level=warn`
2. **Start Registration Service**: Registers itself with Consul
3. **Start Modules**: Discover registration service via Consul, then register themselves

### Verification Commands
```bash
# 1. Check Consul is running
curl http://localhost:8500/v1/catalog/services

# 2. Verify registration service is healthy
curl -s http://localhost:8500/v1/health/service/registration-service

# 3. Check module registration
curl -s http://localhost:8500/v1/health/service/echo
```

## Debugging Common Issues

### Registration Service Health Failing
**Symptoms**: Service registers but health checks fail, gets deregistered
**Causes**:
1. Wrong health check type (HTTP vs gRPC)
2. Incorrect hostname resolution 
3. Profile detection not working

**Debug Steps**:
1. Check service ID pattern in Consul logs
2. Verify profile detection: `registration-service-localhost-38001` (good) vs `registration-service-registration-service-38001` (bad)
3. Ensure gRPC health check, not HTTP

### Module Cannot Find Registration Service  
**Symptoms**: `"UNAVAILABLE: io exception"` or `"No service defined for name localhost"`
**Causes**:
1. Missing `quarkus.grpc.clients.registration-service.host=registration-service`
2. Registration service not registered in Consul
3. Stork configuration incorrect

**Debug Steps**:
1. Verify registration service is in Consul and healthy
2. Check gRPC client target in logs: should be `stork://registration-service` not `stork://localhost`
3. Verify all three Stork configuration properties are present

### Hostname Resolution Issues
**Symptoms**: Services register but can't connect to each other; "Connection refused" errors
**Root Cause**: Services binding to specific interfaces (localhost) but advertising different hostnames

**Example Problem**:
- Service binds to `localhost:39100` (127.0.0.1)
- Service advertises as `krick:39100` (127.0.1.1) 
- Other services can't connect because they try to reach the advertised address

**Solution - Bind to All Interfaces**:
```properties
# Bind to all interfaces so service is reachable on any hostname
quarkus.http.host=0.0.0.0
%dev.quarkus.http.host=0.0.0.0
```

**Solution - Control Advertisement Address**:
```properties
# Override what address to advertise for registration
module.registration.host=localhost
```

**Hostname Resolution Priority**:
1. `module.registration.host` (explicit override)
2. `quarkus.http.host` (if not 0.0.0.0)
3. Environment variables (MODULE_HOST, etc.)
4. System hostname detection

**Debug Commands**:
```bash
# Test if hostname resolves and service is reachable
curl -v http://krick:39100/health
curl -v http://localhost:39100/health

# Test gRPC connectivity  
grpcurl -plaintext krick:39100 grpc.health.v1.Health/Check
grpcurl -plaintext localhost:39100 grpc.health.v1.Health/Check

# Check what addresses services registered with
curl -s http://localhost:8500/v1/health/service/echo | jq '.[] | {ServiceID: .Service.ID, Address: .Service.Address, Port: .Service.Port}'
```

## Best Practices

1. **Follow the Quarkus Way**: Use `@ConfigProperty` injection instead of system properties
2. **Match Health Check to Service Type**: gRPC services need gRPC health checks
3. **Consul-Only Approach**: No fallback mechanisms - fail fast if Consul unavailable  
4. **Profile-Based Configuration**: Use `%dev.` prefixes for development overrides
5. **Systematic Debugging**: Trace configuration → code → logs → behavior
6. **Clear Consul Between Tests**: Use `./scripts/clear-consul.sh` for clean starts
7. **Bind to All Interfaces**: Use `quarkus.http.host=0.0.0.0` to ensure 2-way connectivity
8. **Control Advertisement**: Use `module.registration.host` to specify exact hostname for service registration
9. **Test Both Directions**: Verify services can reach each other using `curl` and `grpcurl`
10. **Hostname Resolution Logic**: Follow the priority order: explicit config > Quarkus config > environment > detection

## Error Messages and Solutions

| Error | Cause | Solution |
|-------|-------|----------|
| `"target=stork://localhost:9000"` | Missing `host=registration-service` | Add gRPC client host property |
| `"Check is now critical"` | Wrong health check type | Use `.setGrpc()` not `.setHttp()` |
| `"service-registration-service-38001"` | Profile detection failing | Use `@ConfigProperty(name = "quarkus.profile")` |
| `"UNAVAILABLE: io exception"` | Registration service not found | Verify registration service is healthy in Consul |
| `"deregister interval below minimum"` | Timeout too low | Use minimum `"1m"` for deregister timeout |
| `"Connection refused: krick:38001"` | Hostname binding mismatch | Use `quarkus.http.host=0.0.0.0` |
| `"Cannot connect to advertised address"` | Service binds to localhost only | Bind to all interfaces with `0.0.0.0` |

## Summary

The registration process requires careful attention to:
- **Profile detection** using proper Quarkus configuration injection
- **Health check types** matching service types (gRPC for gRPC services)
- **Hostname resolution** ensuring 2-way connectivity between services
- **Configuration priority** following logical fallback patterns
- **Consul requirements** like minimum timeout values

When properly configured, the system provides automatic service discovery with health monitoring, enabling dynamic scaling and resilient inter-service communication.