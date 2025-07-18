plugins {
    java
    id("io.quarkus")
    `maven-publish`
}



dependencies {
    // Module BOM provides all standard module dependencies
    implementation(platform(project(":bom:module")))

    // Module-specific dependencies only
    implementation("io.quarkus:quarkus-opentelemetry") // Not in module BOM by default

    // DJL (Deep Java Library) for ML inference
    implementation("ai.djl.huggingface:tokenizers:0.33.0")
    implementation("ai.djl.pytorch:pytorch-model-zoo:0.33.0")
    implementation("ai.djl.pytorch:pytorch-jni:2.5.1-0.33.0")

    // CUDA support for GPU acceleration (if on amd64 architecture)
    if (System.getProperty("os.arch") == "amd64") {
        implementation("ai.djl.pytorch:pytorch-native-cu124:2.5.1")
    }

    // Apache Commons for utilities
    implementation("org.apache.commons:commons-lang3")

    // Rokkon commons util for ProcessingBuffer and other utilities
    implementation(project(":commons:util"))

    // Testing dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core")
    testImplementation(project(":testing:util"))
    testImplementation(project(":testing:server-util"))
    testImplementation("io.grpc:grpc-services")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
}

group = "com.rokkon.pipeline"
version = "1.0.0-SNAPSHOT"
description = "embedder"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}



// Exclude integration tests from regular test task
tasks.test {
    exclude("**/*IT.class")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Configuration to consume the CLI jar from cli-register-module
val cliJar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, "cli-jar"))
    }
}

dependencies {
    cliJar(project(":cli:register-module", "cliJar"))
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
            artifactId = "embedder-module"
        }
    }
}
