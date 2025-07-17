plugins {
    alias(libs.plugins.quarkus)
    java
    `maven-publish`
}

dependencies {
    // Module BOM provides all standard module dependencies
    implementation(platform(project(":bom:pipeline-bom")))

    // Quarkus core dependencies
    implementation("io.quarkus:quarkus-arc") // For CDI (Jakarta EE dependency injection)
    implementation("io.quarkus:quarkus-core")
    implementation("io.quarkus:quarkus-grpc") // For gRPC support
    implementation("io.quarkus:quarkus-jackson") // For Jackson JSON processing
    implementation("io.quarkus:quarkus-rest-jackson") // For REST with Jackson
    implementation("io.quarkus:quarkus-smallrye-openapi") // For OpenAPI/Swagger support

    // Utility libraries
    implementation("org.apache.commons:commons-lang3:3.12.0") // For StringUtils

    // Protocol Buffers and model dependencies
    implementation(project(":grpc-stubs")) // For com.rokkon.search.model classes
    implementation(project(":libraries:pipeline-api")) // Common pipeline interfaces
    implementation(project(":libraries:pipeline-commons")) // Common utilities
    implementation(project(":libraries:data-util")) // For data utilities

    // Module-specific dependencies only
    implementation("org.apache.opennlp:opennlp-tools:2.5.4")

    // Module-specific test dependencies
    testImplementation(project(":libraries:testing-commons"))
    testImplementation(project(":libraries:testing-server-util")) // For ProtobufTestDataHelper
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// No gRPC code generation needed - using pre-generated stubs

tasks.test {
    maxHeapSize = "2g"
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Configuration to consume the CLI jar from register-module
val cliJar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, "cli-jar"))
    }
}


// Copy CLI jar for Docker build
tasks.register<Copy>("copyDockerAssets") {
    from(cliJar) {
        rename { "pipeline-cli.jar" }
    }
    into(layout.buildDirectory.dir("docker"))
}

// Hook the copy task before Docker build
tasks.named("quarkusBuild") {
    dependsOn("copyDockerAssets")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "chunker"
        }
    }
}
