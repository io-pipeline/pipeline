plugins {
    `java-library`
    id("io.quarkus")
    id("org.kordamp.gradle.jandex") version "1.1.0"
}

group = "com.pipeline"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(project(":bom:pipeline-bom")))

    // Quarkus extensions
    implementation("io.quarkiverse.config:quarkus-config-consul")
    implementation("io.quarkus:quarkus-hibernate-validator")
    //implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-cache")

    // Stork for service discovery
    implementation("io.smallrye.stork:stork-service-discovery-consul")

    // Vertx Consul client
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")

    // Migrated project dependencies
    implementation(project(":libraries:pipeline-api"))
    implementation(project(":libraries:pipeline-commons"))
    implementation(project(":grpc-stubs"))
    implementation(project(":libraries:validators"))

    // Test dependencies
    testImplementation(project(":libraries:testing-commons"))
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.awaitility:awaitility")
    // https://mvnrepository.com/artifact/com.networknt/json-schema-validator
    implementation("com.networknt:json-schema-validator:1.5.8")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito:mockito-junit-jupiter:5.3.1")
    testImplementation("io.quarkus:quarkus-junit5-mockito")

    // MicroProfile OpenAPI
    implementation("io.quarkus:quarkus-smallrye-openapi")

    // Health check support
    implementation("io.quarkus:quarkus-smallrye-health")

    // Additional Quarkus dependencies
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-context-propagation")

    // Integration test dependencies
    integrationTestImplementation(project(":libraries:testing-commons"))
    
    integrationTestImplementation("io.quarkus:quarkus-junit5")
    integrationTestImplementation("io.rest-assured:rest-assured")
    integrationTestImplementation("org.awaitility:awaitility")
    integrationTestImplementation(libs.assertj)
    integrationTestImplementation("org.junit.jupiter:junit-jupiter-api")
    integrationTestImplementation("io.quarkus:quarkus-test-common")
    integrationTestImplementation("io.quarkus:quarkus-vertx")
    integrationTestImplementation("io.quarkus:quarkus-vertx-http")
    integrationTestImplementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")
    integrationTestImplementation("org.testcontainers:testcontainers")
    integrationTestImplementation("org.testcontainers:consul")
    integrationTestImplementation(project(":libraries:testing-server-util"))
    integrationTestImplementation("org.mockito:mockito-core:5.3.1")
    integrationTestImplementation("com.fasterxml.jackson.core:jackson-databind")
    integrationTestImplementation("org.jboss.logging:jboss-logging")
    integrationTestImplementation("io.quarkus:quarkus-core")
    integrationTestImplementation("jakarta.ws.rs:jakarta.ws.rs-api")
    integrationTestImplementation("org.jboss.resteasy:resteasy-client")



}

tasks.named("compileJava") {
    dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}

tasks.named("compileTestJava") {
    dependsOn(tasks.named("jandex"))
}

// Publish test jar for use by integration tests in other modules
configurations {
    create("testArtifacts")
}

tasks.register<Jar>("testJar") {
    archiveClassifier.set("tests")
    from(sourceSets["test"].output)
}

artifacts {
    add("testArtifacts", tasks["testJar"])
}
