plugins {
    id("io.quarkus")
    id("org.kordamp.gradle.jandex") version "1.1.0"
}

group = "com.rokkon.module"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(project(":bom:pipeline-bom")))

    // Core Quarkus dependencies
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-container-image-docker")

    // gRPC support for module communication
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.grpc:grpc-services") // Includes gRPC health service
    
    // Stork service discovery
    implementation("io.quarkus:quarkus-smallrye-stork")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")

    // Module API and shared dependencies
    implementation(project(":libraries:pipeline-api"))
    implementation(project(":libraries:pipeline-commons"))
    implementation(project(":grpc-stubs"))
    implementation(project(":libraries:data-util")) // For SampleDataLoader

    // Observability
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Test dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation(libs.assertj)
    testImplementation("io.grpc:grpc-services")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(project(":libraries:testing-commons"))

    // Integration test dependencies
    integrationTestImplementation("io.quarkus:quarkus-junit5")
    integrationTestImplementation("io.rest-assured:rest-assured")
    integrationTestImplementation(libs.assertj)
    integrationTestImplementation(project(":libraries:testing-commons"))
}

tasks.named("compileTestJava") {
    dependsOn(tasks.named("jandex"))
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

tasks.named("compileJava") {
    dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}

// Docker tasks disabled until CLI is ready
// TODO: Re-enable when CLI module is available
/*
// Task to copy CLI jar before building Docker image
val copyCLIJar = tasks.register<Copy>("copyCLIJar") {
    dependsOn(":applications:cli:register-module:quarkusBuild")
    from("${project(":applications:cli:register-module").buildDir}/register-module-1.0.0-SNAPSHOT-runner.jar")
    into(layout.buildDirectory.dir("docker"))
    rename { "pipeline-cli.jar" }
}

// Configure Quarkus to build Docker images
tasks.named("quarkusBuild") {
    dependsOn(copyCLIJar)
}

// Task to build Docker image
val buildDockerImage = tasks.register<Exec>("buildDockerImage") {
    dependsOn("quarkusBuild", copyCLIJar)

    workingDir = projectDir
    commandLine("docker", "build", 
        "-f", "src/main/docker/Dockerfile.jvm",
        "-t", "pipeline/echo:latest",
        "-t", "pipeline/echo:${project.version}",
        ".")

    doFirst {
        println("Building Docker image for echo module...")
    }
}
*/
