plugins {
    java
    id("io.quarkus")
}

dependencies {
    // Library BOM provides gRPC code generation without server components
    implementation(platform(project(":bom:library")))
    
    // --- Core dependencies ---
    implementation("io.grpc:grpc-services")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-vertx")
    
    // --- Service Discovery ---
    implementation("io.quarkus:quarkus-smallrye-stork")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    implementation("io.smallrye.stork:stork-configuration-generator")
    
    // --- Consul Client ---
    implementation("io.vertx:vertx-consul-client")
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")
    
    // --- Caching ---
    implementation("io.quarkus:quarkus-cache")
    
    // --- Commons dependencies ---
    implementation(project(":commons:interface"))
    implementation(project(":commons:grpc-stubs"))
    
    // --- Testing ---
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.testcontainers:consul")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(project(":testing:util"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
