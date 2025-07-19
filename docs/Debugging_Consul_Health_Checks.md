# Debugging Consul Health Checks

This document captures the systematic debugging process we used to fix registration service health check failures.

## Problem Summary

The registration service was registering with Consul but health checks were failing, causing the service to be deregistered after the timeout period.

## Symptoms

```
2025-07-19T11:26:06.785-0400 [WARN]  agent: Check is now critical: check=service:registration-service-registration-service-38001
2025-07-19T11:29:03.333-0400 [INFO]  agent: deregistered service with critical health due to exceeding health check's 'deregister_critical_service_after' timeout
```

## Root Cause Analysis

### Issue 1: Incorrect Profile Detection

**Problem**: Code used `System.getProperty("quarkus.profile", "prod")` which always returned "prod"
**Result**: Hostname logic fell through to return `applicationName` instead of "localhost"
**Service ID**: `registration-service-registration-service-38001` (hostname = "registration-service")

**Solution**: Use proper Quarkus injection
```java
@ConfigProperty(name = "quarkus.profile")
String profile;
```

### Issue 2: Wrong Health Check Type

**Problem**: Used HTTP health check for a gRPC service
```java
.setHttp("http://" + hostname + ":" + httpPort + "/q/health")
```

**Solution**: Use gRPC health check
```java
.setGrpc(hostname + ":" + httpPort)
```

### Issue 3: Deregister Timeout Too Low

**Problem**: Used `"30s"` but Consul requires minimum `"1m"`
**Solution**: Changed to `"1m"`

### Issue 4: Hostname Resolution and 2-Way Connectivity

**Problem**: Services binding to specific interfaces but advertising different hostnames
**Example**: 
- Echo service binds to `localhost:39100` (127.0.0.1)
- Echo advertises as `krick:39100` (127.0.1.1)
- Registration service can't reach echo for verification

**Root Cause**: `PipelineAutoRegistrationBean.determineHost()` uses system hostname but service binds to localhost only

**Solution**: Enhanced hostname resolution logic
```java
// Added to PipelineAutoRegistrationBean
@ConfigProperty(name = "quarkus.http.host")
Optional<String> quarkusHttpHost;

// Updated determineHost() logic:
// 1. module.registration.host (explicit override)
// 2. quarkus.http.host (if not 0.0.0.0) 
// 3. Environment variables
// 4. System hostname detection
```

**Configuration Solution**:
```properties
# Bind to all interfaces for 2-way connectivity
quarkus.http.host=0.0.0.0

# Control advertisement address
module.registration.host=localhost
```

## Debugging Methodology

### 1. Think Systematically
- Don't guess - trace the actual code execution
- Connect configuration to logs to understand what's happening
- Follow the Quarkus way instead of hacking solutions

### 2. Trace Service Registration
```bash
# Check what services are registered
curl -s http://localhost:8500/v1/catalog/services

# Check health status of specific service
curl -s http://localhost:8500/v1/health/service/registration-service
```

### 3. Analyze Service ID Pattern
Service ID format: `applicationName + "-" + hostname + "-" + httpPort`
- Before fix: `registration-service-registration-service-38001`
- After fix: `registration-service-localhost-38001`

## Verification

After all fixes were applied:

**Registration Service**:
```json
{
  "CheckID": "service:registration-service-localhost-38001",
  "Name": "Registration Service Health Check", 
  "Status": "passing",
  "Output": "gRPC check localhost:38001: success",
  "Type": "grpc"
}
```

**Echo Module**:
```json
{
  "CheckID": "service:echo-localhost-39100",
  "Name": "Module Health Check",
  "Status": "passing", 
  "Output": "gRPC check localhost:39100/grpc.health.v1.Health: success",
  "Type": "grpc"
}
```

**2-Way Connectivity Tests**:
```bash
# Registration service reachable on both hostnames
curl http://localhost:38001/health  # ✅ Works
curl http://krick:38001/health      # ✅ Works

# Echo service reachable on both hostnames  
curl http://localhost:39100/health  # ✅ Works
curl http://krick:39100/health      # ✅ Works

# gRPC connectivity verified
grpcurl -plaintext localhost:38001 grpc.health.v1.Health/Check  # ✅ SERVING
grpcurl -plaintext krick:38001 grpc.health.v1.Health/Check      # ✅ SERVING
grpcurl -plaintext localhost:39100 grpc.health.v1.Health/Check  # ✅ SERVING  
grpcurl -plaintext krick:39100 grpc.health.v1.Health/Check      # ✅ SERVING
```

## Key Learnings

1. **Use Quarkus patterns**: Always inject configuration with `@ConfigProperty` instead of system properties
2. **Match health check to service type**: gRPC services need gRPC health checks
3. **Follow external requirements**: Consul has minimum timeout requirements
4. **Debug systematically**: Trace from configuration → code → logs → behavior
5. **Test each fix**: Verify each change resolves the specific issue

## Prevention

- Always use `@ConfigProperty(name = "quarkus.profile")` for profile detection
- Use `.setGrpc()` for gRPC services, `.setHttp()` for HTTP services  
- Set deregister timeout to minimum `"1m"` for Consul
- Use `quarkus.http.host=0.0.0.0` to bind to all interfaces for 2-way connectivity
- Test health endpoints manually: `curl http://localhost:38001/health`
- Test both hostnames: `curl http://krick:38001/health` and `curl http://localhost:38001/health`
- Verify gRPC connectivity: `grpcurl -plaintext localhost:38001 grpc.health.v1.Health/Check`
- Verify service registration: `curl http://localhost:8500/v1/health/service/{service-name}`

## Production Notes

**Important**: These hostname resolution complexities are specific to local development. In production:
- **Consul sidecars** handle service discovery and connectivity
- **Container networking** provides proper hostname resolution
- **Service mesh** manages inter-service communication
- Local development patterns (binding to 0.0.0.0, localhost advertisement) are not needed

The techniques documented here are primarily for local development and testing scenarios.