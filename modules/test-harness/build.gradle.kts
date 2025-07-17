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
    implementation("io.quarkus:quarkus-scheduler")

    // gRPC support for module communication
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.grpc:grpc-services") // Includes gRPC health service
    implementation("io.grpc:grpc-census")


    // Module API and shared dependencies
    implementation(project(":libraries:pipeline-api"))
    implementation(project(":libraries:pipeline-commons"))
    implementation(project(":grpc-stubs"))
    implementation(project(":libraries:data-util")) // For test data generation
    testImplementation(project(":libraries:testing-commons")) // For test harness proto definitions

    // Observability
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Consul support (for service discovery in tests)
    implementation("io.quarkiverse.config:quarkus-config-consul")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")
    implementation("io.smallrye.stork:stork-core")

    

    // Test dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation(libs.assertj)
    testImplementation("io.grpc:grpc-services")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(project(":libraries:testing-commons"))
    testImplementation(project(":libraries:testing-server-util"))
    testImplementation("io.smallrye.stork:stork-test-utils")

    // Integration test dependencies
    integrationTestImplementation("io.quarkus:quarkus-junit5")
    integrationTestImplementation("io.rest-assured:rest-assured")
    integrationTestImplementation(libs.assertj)
    integrationTestImplementation(project(":libraries:testing-commons"))
    integrationTestImplementation("com.github.docker-java:docker-java-transport-httpclient5")
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