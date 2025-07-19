# Module Conversion Guide: Building Pipeline Engine Modules

**Last Updated: July 19, 2025**

This comprehensive guide provides the definitive process for converting existing modules or creating new modules for the Pipeline Engine. All modules must follow these patterns to ensure consistency, reliability, and seamless integration.

**🏆 GOLDEN STANDARD: Echo Module**  
The `modules/echo` is our reference implementation. **ALWAYS** refer to Echo as the example when implementing any pattern described in this guide.

---

## I. Core Principles

### Architecture Foundation
- **Pipeline-First Design**: All modules are pipeline steps that process data streams
- **gRPC Communication**: Primary interface using Protocol Buffers
- **Consul Service Discovery**: No hardcoded endpoints, everything discovered via Consul
- **Quarkus Framework**: Leveraging Quarkus patterns and best practices
- **Self-Registration**: Modules automatically register with the registration service
- **Unified Testing Strategy**: Comprehensive testing with proper mocking and integration patterns

### Package Structure
- **New namespace**: `io.pipeline.module.[module-name]` 
- **No suffixes**: Service names are clean (e.g., `echo`, not `echo-module`)
- **Consistent naming**: Application name = service name = module directory name

---

## II. Prerequisites

Before starting module conversion:

✅ **Registration service running**: Core service discovery infrastructure  
✅ **Consul running**: `consul agent -dev -log-level=warn`  
✅ **Echo module working**: Verify the golden standard is functional  
✅ **Understand testing patterns**: Review testing strategy and Echo's tests  
✅ **BOM structure**: Understand shared dependencies and version management  

---

## III. Step-by-Step Conversion Process

### Phase 1: Project Creation and Structure

#### 1.1 Create New Module with Quarkus CLI

**Always start with Quarkus CLI** - don't copy from existing modules:

```bash
# Navigate to modules directory
cd modules/

# Create new module using Quarkus CLI
quarkus create app [module-name] \
  --java=21 \
  --gradle-flavor=groovy \
  --extension=grpc,smallrye-health,quarkus-resteasy-reactive-jackson

# Move to correct location
mv [module-name] [module-name]/
```

**Example for a 'parser' module:**
```bash
cd modules/
quarkus create app parser \
  --java=21 \
  --gradle-flavor=groovy \
  --extension=grpc,smallrye-health,quarkus-resteasy-reactive-jackson
```

#### 1.2 Update Package Structure

**Rename packages** to follow our standard:
```
src/main/java/org/acme/ → src/main/java/io/pipeline/module/[module-name]/
src/test/java/org/acme/ → src/test/java/io/pipeline/module/[module-name]/
```

**Example for parser module:**
```
src/main/java/org/acme/ → src/main/java/io/pipeline/module/parser/
```

#### 1.3 Add to Root Project

Update root `settings.gradle`:
```gradle
include ':modules:[module-name]'
```

### Phase 2: Build Configuration

#### 2.1 Configure build.gradle

Replace the generated `build.gradle` with our standard pattern (**refer to echo/build.gradle**):

```gradle
plugins {
    id 'java'
    id 'io.quarkus'
    id 'org.kordamp.gradle.jandex'
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation platform(project(':libraries:pipeline-commons'))
    implementation project(':libraries:pipeline-api')
    implementation project(':grpc-stubs')
    implementation project(':libraries:data-util')
    
    // Core Quarkus dependencies
    implementation 'io.quarkus:quarkus-arc'
    implementation 'io.quarkus:quarkus-grpc'
    implementation 'io.quarkus:quarkus-smallrye-health'
    implementation 'io.quarkus:quarkus-rest'
    implementation 'io.quarkus:quarkus-rest-jackson'
    
    // Testing dependencies  
    testImplementation 'io.quarkus:quarkus-junit5'
    testImplementation 'io.rest-assured:rest-assured'
    testImplementation 'org.awaitility:awaitility'
    testImplementation 'org.hamcrest:hamcrest'
    testImplementation 'org.hamcrest:hamcrest-library'
}

// Jandex plugin for CDI scanning
jandex {
    includeInJar = true
}
```

#### 2.2 Add Integration Test Support

Add to `build.gradle`:
```gradle
sourceSets {
    integrationTest {
        java {
            srcDir 'src/integrationTest/java'
            compileClasspath += main.output + test.output
            runtimeClasspath += main.output + test.output
        }
        resources {
            srcDir 'src/integrationTest/resources'
        }
    }
}

configurations {
    integrationTestImplementation.extendsFrom implementation
    integrationTestRuntimeOnly.extendsFrom runtimeOnly
}

dependencies {
    integrationTestImplementation 'io.quarkus:quarkus-junit5'
    integrationTestImplementation 'io.quarkus:quarkus-test-common'
}

tasks.register('quarkusIntTest', Test) {
    description = 'Runs Quarkus integration tests'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    shouldRunAfter test
}
```

### Phase 3: Configuration Setup

#### 3.1 Create application.properties

Create `src/main/resources/application.properties` following **Echo's pattern exactly**:

```properties
# Module identification
module.name=[module-name]
quarkus.application.name=[module-name]

# Port allocation - bind to all interfaces for 2-way connectivity
quarkus.http.port=[production-port]
quarkus.http.host=0.0.0.0
quarkus.grpc.server.use-separate-server=false

# Development override
%dev.quarkus.http.port=[dev-port]
%dev.quarkus.http.host=0.0.0.0

# Health Configuration
quarkus.smallrye-health.root-path=/health

# Enable OpenAPI
quarkus.swagger-ui.always-include=true
quarkus.swagger-ui.path=/swagger-ui

# Logging Configuration
quarkus.log.level=INFO
quarkus.log.category."io.pipeline".level=DEBUG

# Registration Service Discovery via Consul (required)
quarkus.grpc.clients.registration-service.host=registration-service
quarkus.grpc.clients.registration-service.name-resolver=stork
quarkus.stork.registration-service.service-discovery.type=consul
quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:consul}
quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}

# Development override - use localhost in dev mode
%dev.quarkus.stork.registration-service.service-discovery.consul-host=localhost

# Module registration configuration - control advertisement address
module.registration.host=localhost

# gRPC Message Size Configuration (2GB - 1 byte, max int value)
quarkus.grpc.server.max-inbound-message-size=2147483647

# Test settings  
%test.quarkus.http.port=0

# JVM Memory settings for large message processing
quarkus.native.additional-build-args=-J-Xmx4g
%dev.quarkus.devservices.timeout=120s
```

#### 3.2 Port Allocation Strategy

Follow the documented port allocation:
- **Registration Service**: 38501 (prod) / 38001 (dev)
- **Echo**: 39000 (prod) / 39100 (dev)  
- **Parser**: 39010 (prod) / 39110 (dev)
- **Chunker**: 39020 (prod) / 39120 (dev)
- **Embedder**: 39030 (prod) / 39130 (dev)
- **etc.**

### Phase 4: Core Implementation

#### 4.1 Create Main Service Class

Implement your main gRPC service following **Echo's EchoServiceImpl pattern**:

```java
package io.pipeline.module.[module-name];

import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.data.module.PipeStepProcessor;
import io.quarkus.grpc.GrpcService;
import org.jboss.logging.Logger;

@GrpcService
@PipelineAutoRegister(
    moduleType = "processor",
    useHttpPort = true,
    metadata = {"category=data-processing"}
)
public class [ModuleName]ServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger([ModuleName]ServiceImpl.class);
    
    // Implementation following Echo pattern
}
```

**Critical Points:**
- ✅ Use `@PipelineAutoRegister` for automatic registration
- ✅ Implement `PipeStepProcessor` interface  
- ✅ Use JBoss logging, **NEVER** `System.out.println`
- ✅ Follow Echo's implementation patterns exactly

#### 4.2 Add Resource Endpoints (if needed)

Create REST endpoints following Echo's pattern:
```java
package io.pipeline.module.[module-name];

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@Path("/[module-name]")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class [ModuleName]Resource {
    
    private static final Logger LOG = Logger.getLogger([ModuleName]Resource.class);
    
    // Implementation following Echo pattern
}
```

### Phase 5: Testing Implementation

#### 5.1 Unit Tests Pattern

Create `src/test/java/io/pipeline/module/[module-name]/[ModuleName]ServiceTest.java`:

```java
package io.pipeline.module.[module-name];

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.jboss.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class [ModuleName]ServiceTest {
    
    private static final Logger LOG = Logger.getLogger([ModuleName]ServiceTest.class);
    
    @InjectMock
    // Mock external dependencies following Echo pattern
    
    @Test
    void shouldProcessDataCorrectly() {
        // Use Hamcrest assertions for detailed debugging
        assertThat("result should not be null", result, is(notNullValue()));
        assertThat("result should contain expected data", result.getData(), containsString("expected"));
    }
}
```

**Testing Requirements:**
- ✅ Use `@QuarkusTest` for unit tests
- ✅ Use `@InjectMock` for mocking external dependencies  
- ✅ Use **Hamcrest assertions** for detailed debugging messages
- ✅ Use JBoss logging in tests, **NEVER** `System.out.println`
- ✅ Follow Echo's test patterns exactly

#### 5.2 Integration Tests Pattern  

Create `src/integrationTest/java/io/pipeline/module/[module-name]/[ModuleName]ServiceIT.java`:

```java
package io.pipeline.module.[module-name];

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusIntegrationTest
class [ModuleName]ServiceIT {
    
    private static final Logger LOG = Logger.getLogger([ModuleName]ServiceIT.class);
    
    @Test
    void shouldRegisterWithConsulSuccessfully() {
        // Integration test against real JAR
        // Use local Consul for testing
        // Follow Echo's integration test patterns
    }
}
```

**Integration Testing Requirements:**
- ✅ Use `@QuarkusIntegrationTest` - tests run against actual JAR
- ✅ **NO CDI injection** in integration tests - tests run against external JAR
- ✅ Use local Consul for service discovery testing
- ✅ Build objects manually or use REST endpoints to test functionality
- ✅ Follow Echo's integration test patterns exactly

#### 5.3 Test Data Strategy

**Use centralized test data** from `libraries/data-util/src/main/resources/test-data/`:

```java
// Load test data using data-util
@Inject
SampleDataLoader dataLoader;

@Test
void shouldProcessSampleData() {
    PipeStreamData testData = dataLoader.loadSamplePipeStream("sample-document.json");
    // Process with your module
}
```

**Available test data sources:**
- Sample documents (PDF, Word, text)
- Sample pipe streams
- Parser test data
- Embedding test vectors

### Phase 6: Consul Integration

#### 6.1 Local Development Setup

**Start Consul for development:**
```bash
consul agent -dev -log-level=warn
```

**Verify Consul connectivity:**
```bash
curl http://localhost:8500/v1/catalog/services
```

#### 6.2 Service Registration Verification

**After starting your module, verify registration:**
```bash
# Check service is registered
curl -s http://localhost:8500/v1/catalog/services

# Check service health  
curl -s http://localhost:8500/v1/health/service/[module-name]

# Test gRPC health
grpcurl -plaintext localhost:[dev-port] grpc.health.v1.Health/Check
```

### Phase 7: Dependencies and BOM Management

#### 7.1 Shared Dependencies

**Core dependencies managed by pipeline-commons BOM:**
- Quarkus platform
- gRPC and Protocol Buffers
- SmallRye (Mutiny, Health, OpenAPI)
- Testing libraries (JUnit 5, Hamcrest, Mockito)
- Logging (JBoss)

#### 7.2 Module-Specific Dependencies

**Add only module-specific dependencies** not covered by BOM:
```gradle
dependencies {
    // Only add dependencies specific to your module
    implementation 'org.apache.tika:tika-core:2.9.1'  // Example for parser
}
```

### Phase 8: Web Service Dependencies (if needed)

For modules requiring web interfaces, add **exactly** these dependencies:

```gradle
dependencies {
    implementation 'io.quarkus:quarkus-rest'
    implementation 'io.quarkus:quarkus-rest-jackson'
    implementation 'io.quarkus:quarkus-smallrye-openapi'
    testImplementation 'io.rest-assured:rest-assured'
}
```

---

## IV. Quality Assurance Checklist

Before considering conversion complete, verify **ALL** these items:

### Code Quality
- [ ] **NO** `System.out.println` anywhere - use JBoss logging only
- [ ] All log statements use appropriate levels (DEBUG, INFO, WARN, ERROR)
- [ ] Package structure follows `io.pipeline.module.[module-name]`
- [ ] Service class uses `@PipelineAutoRegister` annotation
- [ ] Application name matches module directory name (no `-module` suffix)

### Configuration
- [ ] `application.properties` follows Echo's pattern exactly
- [ ] Port allocation follows documented strategy
- [ ] Consul service discovery configured correctly  
- [ ] `module.registration.host=localhost` set
- [ ] `quarkus.http.host=0.0.0.0` configured for 2-way connectivity
- [ ] gRPC message size configured for 2GB

### Testing
- [ ] Unit tests use `@QuarkusTest` and Hamcrest assertions
- [ ] Integration tests use `@QuarkusIntegrationTest`
- [ ] **NO** CDI injection in integration tests
- [ ] All tests use JBoss logging, **NEVER** `System.out.println`
- [ ] Test data loaded from centralized `data-util` when possible

### Build Configuration  
- [ ] `build.gradle` follows Echo's pattern exactly
- [ ] Jandex plugin configured for CDI scanning
- [ ] Integration test source sets configured
- [ ] BOM dependencies used instead of direct versions
- [ ] `quarkusIntTest` task configured

### Service Discovery
- [ ] Module registers successfully with Consul
- [ ] Health checks pass (both HTTP and gRPC)
- [ ] Service discoverable by other modules
- [ ] Registration service can connect to module

### Documentation
- [ ] Module added to project documentation
- [ ] Any special setup requirements documented
- [ ] API endpoints documented if applicable

---

## V. Running and Testing

### Local Development
```bash
# Start Consul
consul agent -dev -log-level=warn

# Start registration service
./gradlew :applications:registration-service:quarkusDev

# Start your module  
./gradlew :modules:[module-name]:quarkusDev

# Run tests
./gradlew :modules:[module-name]:test
./gradlew :modules:[module-name]:quarkusIntTest
```

### Verification Commands
```bash
# Clear Consul (if needed)
./scripts/clear-consul.sh

# Check module health
curl http://localhost:[dev-port]/health

# Test gRPC  
grpcurl -plaintext localhost:[dev-port] grpc.health.v1.Health/Check

# Verify Consul registration
curl -s http://localhost:8500/v1/health/service/[module-name]
```

---

## VI. Common Patterns Reference

### Service Implementation Template
```java
package io.pipeline.module.[module-name];

import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.data.module.PipeStepProcessor;
import io.pipeline.data.module.PipeStreamData;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

@GrpcService
@PipelineAutoRegister(
    moduleType = "processor",
    useHttpPort = true,
    metadata = {"category=data-processing", "type=parser"}
)
public class [ModuleName]ServiceImpl implements PipeStepProcessor {
    
    private static final Logger LOG = Logger.getLogger([ModuleName]ServiceImpl.class);
    
    @Override
    public Uni<PipeStreamData> process(PipeStreamData request) {
        LOG.infof("Processing data with %d bytes", request.getData().size());
        
        // Your implementation here
        
        return Uni.createFrom().item(processedData);
    }
}
```

### Test Template
```java
package io.pipeline.module.[module-name];

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class [ModuleName]ServiceTest {
    
    private static final Logger LOG = Logger.getLogger([ModuleName]ServiceTest.class);
    
    @Test
    void shouldProcessDataSuccessfully() {
        // Given
        PipeStreamData inputData = createTestData();
        LOG.debug("Testing with input data");
        
        // When  
        PipeStreamData result = service.process(inputData).await().indefinitely();
        
        // Then
        assertThat("Result should not be null", result, is(notNullValue()));
        assertThat("Result should contain processed data", 
                   result.getData(), is(not(empty())));
        LOG.debug("Test completed successfully");
    }
}
```

---

## VII. Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Module not registering | Missing registration config | Add Stork service discovery config |
| Health checks failing | Wrong health check type | Ensure gRPC health checks enabled |
| Port conflicts | Multiple modules on same port | Check port allocation strategy |
| Connection refused | Wrong host binding | Use `quarkus.http.host=0.0.0.0` |
| Split package errors | Multiple proto sources | Disable gRPC generation in module |
| CDI issues | Missing Jandex | Add Jandex plugin to build |

### Debug Commands
```bash
# Check what's listening on ports
ss -tlnp | grep [port]

# Test connectivity
curl -v http://localhost:[port]/health
grpcurl -plaintext localhost:[port] grpc.health.v1.Health/Check

# Check Consul status
curl -s http://localhost:8500/v1/health/service/[module-name] | jq

# Clear Consul for fresh start
./scripts/clear-consul.sh
```

---

## VIII. Before/After Conversion Examples

The following examples show concrete transformations from existing modules to the new standards.

### Example 1: Package Structure Conversion

**❌ BEFORE (embedder module):**
```java
package com.rokkon.modules.embedder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
@Singleton
public class EmbedderService implements PipeStepProcessor {
    private static final Logger log = LoggerFactory.getLogger(EmbedderService.class);
    // Implementation
}
```

**✅ AFTER (following Echo pattern):**
```java
package io.pipeline.module.embedder;

import org.jboss.logging.Logger;
import io.pipeline.api.annotation.PipelineAutoRegister;

@GrpcService
@PipelineAutoRegister(
    moduleType = "processor",
    useHttpPort = true,
    metadata = {"category=embedding", "type=ml-model"}
)
public class EmbedderServiceImpl implements PipeStepProcessor {
    private static final Logger LOG = Logger.getLogger(EmbedderServiceImpl.class);
    // Implementation
}
```

**Key Changes:**
- ✅ Package: `com.rokkon.modules.embedder` → `io.pipeline.module.embedder`
- ✅ Class name: `EmbedderService` → `EmbedderServiceImpl` (consistency with Echo)
- ✅ Logging: `org.slf4j.Logger` → `org.jboss.logging.Logger`
- ✅ Logger variable: `log` → `LOG` (consistent with Echo pattern)
- ✅ Added `@PipelineAutoRegister` annotation for auto-registration
- ✅ Removed `@Singleton` (not needed with `@GrpcService`)

### Example 2: Configuration File Conversion

**❌ BEFORE (chunker module application.yml):**
```yaml
quarkus:
  application:
    name: chunker
  http:
    port: 39100
  "%dev":
    http:
      port: 38004
  grpc:
    server:
      use-separate-server: false
      host: 0.0.0.0
      enable-reflection-service: true
```

**✅ AFTER (following Echo pattern application.properties):**
```properties
# Module identification
module.name=chunker
quarkus.application.name=chunker

# Port allocation - bind to all interfaces for 2-way connectivity
quarkus.http.port=39020
quarkus.http.host=0.0.0.0
quarkus.grpc.server.use-separate-server=false

# Development override
%dev.quarkus.http.port=39120
%dev.quarkus.http.host=0.0.0.0

# Health Configuration
quarkus.smallrye-health.root-path=/health

# Registration Service Discovery via Consul (required)
quarkus.grpc.clients.registration-service.host=registration-service
quarkus.grpc.clients.registration-service.name-resolver=stork
quarkus.stork.registration-service.service-discovery.type=consul
quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:consul}
quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}

# Development override - use localhost in dev mode
%dev.quarkus.stork.registration-service.service-discovery.consul-host=localhost

# Module registration configuration - control advertisement address
module.registration.host=localhost

# gRPC Message Size Configuration (2GB - 1 byte, max int value)
quarkus.grpc.server.max-inbound-message-size=2147483647

# Logging Configuration
quarkus.log.level=INFO
quarkus.log.category."io.pipeline".level=DEBUG
```

**Key Changes:**
- ✅ Format: YAML → properties file (consistent with Echo)
- ✅ Added module identification fields
- ✅ Added Consul service discovery configuration
- ✅ Added proper port allocation (chunker: 39020 prod, 39120 dev)
- ✅ Added `module.registration.host=localhost` for hostname control
- ✅ Added gRPC message size configuration
- ✅ Updated logging configuration for new package structure

### Example 3: Service Registration Method Conversion

**❌ BEFORE (manual registration in chunker):**
```java
@Override
public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
    return Uni.createFrom().item(() -> {
        try {
            ServiceRegistrationResponse.Builder registrationBuilder = ServiceRegistrationResponse.newBuilder()
                    .setModuleName("chunker")
                    .setJsonConfigSchema(ChunkerOptions.getJsonV7Schema())
                    .setHealthCheckPassed(true)
                    .setHealthCheckMessage("Chunker module is healthy and ready to process documents");

            LOG.info("Returned service registration for chunker module");
            return registrationBuilder.build();
        } catch (Exception e) {
            // Error handling
        }
    });
}
```

**✅ AFTER (automatic registration with annotation):**
```java
@GrpcService
@PipelineAutoRegister(
    moduleType = "processor",
    useHttpPort = true,
    metadata = {"category=data-processing", "type=chunker"}
)
public class ChunkerServiceImpl implements PipeStepProcessor {
    // No manual registration code needed!
    // Registration happens automatically via annotation
}
```

**Key Changes:**
- ✅ Removed manual `getServiceRegistration()` implementation
- ✅ Added `@PipelineAutoRegister` annotation for automatic registration
- ✅ Service discovery and health checks handled automatically
- ✅ Simplified code by removing boilerplate registration logic

### Example 4: Logging Pattern Conversion

**❌ BEFORE (mixed logging patterns):**
```java
// In embedder module
private static final Logger log = LoggerFactory.getLogger(EmbedderService.class);

// In parser module  
private static final Logger LOG = Logger.getLogger(ParserServiceImpl.class);

// Mixed usage
log.info("Processing document ID: {}", inputDoc.getId());
LOG.debugf("Successfully parsed document - title: '%s'", outputDoc.getTitle());
```

**✅ AFTER (consistent JBoss logging following Echo):**
```java
// Consistent pattern across all modules
private static final Logger LOG = Logger.getLogger(EmbedderServiceImpl.class);

// Consistent usage with format strings
LOG.infof("Processing document ID: %s", inputDoc.getId());
LOG.debugf("Successfully parsed document - title: %s", outputDoc.getTitle());

// Never use System.out.println - use logging
LOG.debug("Processing started");  // ✅ Good
System.out.println("Processing started");  // ❌ Never do this
```

**Key Changes:**
- ✅ Import: `org.slf4j.Logger` → `org.jboss.logging.Logger`
- ✅ Variable name: `log` → `LOG` (consistent with Echo)
- ✅ Usage pattern: `.info("{}", value)` → `.infof("format %s", value)`
- ✅ Eliminated all `System.out.println` usage

### Example 5: Test Pattern Conversion

**❌ BEFORE (old test patterns):**
```java
// Mixed test patterns with different assertion styles
@QuarkusTest
class EmbedderServiceTest {
    
    @Test
    void shouldProcessDocumentFields() {
        // Old assertion pattern
        Assertions.assertTrue(result.getSuccess());
        Assertions.assertEquals("expected", result.getData());
    }
}
```

**✅ AFTER (following Echo test patterns):**
```java
// Consistent Hamcrest assertions following Echo
@QuarkusTest
class EmbedderServiceTest {
    
    private static final Logger LOG = Logger.getLogger(EmbedderServiceTest.class);
    
    @Test
    void shouldProcessDocumentFields() {
        // Given
        PipeStreamData inputData = createTestData();
        LOG.debug("Testing with input data");
        
        // When
        PipeStreamData result = service.process(inputData).await().indefinitely();
        
        // Then - Hamcrest assertions with descriptive messages
        assertThat("Result should not be null", result, is(notNullValue()));
        assertThat("Result should contain processed data", 
                   result.getData(), is(not(empty())));
        assertThat("Processing should be successful", 
                   result.getSuccess(), is(true));
        LOG.debug("Test completed successfully");
    }
}
```

**Key Changes:**
- ✅ Added JBoss logging to tests (no `System.out.println`)
- ✅ Used Hamcrest assertions with descriptive messages
- ✅ Added Given/When/Then structure
- ✅ Used `await().indefinitely()` for reactive testing

### Example 6: Build Configuration Conversion

**❌ BEFORE (chunker build.gradle.kts with Kotlin DSL):**
```kotlin
plugins {
    id("java")
    id("io.quarkus")
}

dependencies {
    implementation(enforcedPlatform("${quarkus.platform.group-id}:${quarkus.platform.artifact-id}:${quarkus.platform.version}"))
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-smallrye-health")
    // Direct version management
}
```

**✅ AFTER (following Echo build.gradle with Groovy DSL):**
```gradle
plugins {
    id 'java'
    id 'io.quarkus'
    id 'org.kordamp.gradle.jandex'
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation platform(project(':libraries:pipeline-commons'))
    implementation project(':libraries:pipeline-api')
    implementation project(':grpc-stubs')
    implementation project(':libraries:data-util')
    
    // Core Quarkus dependencies
    implementation 'io.quarkus:quarkus-arc'
    implementation 'io.quarkus:quarkus-grpc'
    implementation 'io.quarkus:quarkus-smallrye-health'
    implementation 'io.quarkus:quarkus-rest'
    implementation 'io.quarkus:quarkus-rest-jackson'
    
    // Testing dependencies  
    testImplementation 'io.quarkus:quarkus-junit5'
    testImplementation 'io.rest-assured:rest-assured'
    testImplementation 'org.awaitility:awaitility'
    testImplementation 'org.hamcrest:hamcrest'
    testImplementation 'org.hamcrest:hamcrest-library'
}

// Jandex plugin for CDI scanning
jandex {
    includeInJar = true
}
```

**Key Changes:**
- ✅ Build DSL: Kotlin DSL → Groovy DSL (consistent with Echo)
- ✅ Added Jandex plugin for CDI scanning
- ✅ Use BOM for dependency management via `pipeline-commons`
- ✅ Added required project dependencies (pipeline-api, grpc-stubs, data-util)
- ✅ Added Hamcrest for testing assertions

### Example 7: Complete Module Directory Structure

**❌ BEFORE (embedder module structure):**
```
modules/embedder/
├── build.gradle.kts                    # Kotlin DSL
├── src/main/resources/application.yml  # YAML config
└── src/main/java/com/rokkon/modules/embedder/  # Old package
    ├── EmbedderService.java            # Missing auto-registration
    └── EmbedderOptions.java
```

**✅ AFTER (following Echo structure):**
```
modules/embedder/
├── build.gradle                       # Groovy DSL like Echo
├── src/main/resources/application.properties  # Properties file like Echo
├── src/main/java/io/pipeline/module/embedder/  # New package structure
│   ├── EmbedderServiceImpl.java       # With @PipelineAutoRegister
│   ├── EmbedderResource.java          # REST endpoints if needed
│   └── EmbedderOptions.java
├── src/test/java/io/pipeline/module/embedder/  # Test package structure
│   └── EmbedderServiceTest.java       # Hamcrest assertions
└── src/integrationTest/java/io/pipeline/module/embedder/  # Integration tests
    └── EmbedderServiceIT.java         # @QuarkusIntegrationTest
```

**Key Changes:**
- ✅ Directory structure matches Echo exactly
- ✅ Package structure follows `io.pipeline.module.[name]` pattern
- ✅ Configuration file uses properties format
- ✅ Build file uses Groovy DSL
- ✅ Added proper test structure with integration tests

---

## IX. Success Criteria

A module conversion is complete when:

1. ✅ **Module follows Echo patterns exactly**
2. ✅ **All tests pass** (unit and integration)  
3. ✅ **Module registers with Consul successfully**
4. ✅ **Health checks pass** (HTTP and gRPC)
5. ✅ **Other modules can discover it via Stork**
6. ✅ **No hardcoded dependencies or System.out.println**
7. ✅ **Documentation updated**
8. ✅ **Code quality checklist completed**

**Remember: Echo is the golden standard. When in doubt, refer to Echo's implementation.**