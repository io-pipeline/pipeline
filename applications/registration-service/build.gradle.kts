plugins {
    id("io.quarkus")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Enforce JDK 21 at build time
tasks.withType<JavaCompile> {
    options.release.set(21)
    
    doFirst {
        val javaVersion = JavaVersion.current()
        if (javaVersion < JavaVersion.VERSION_21) {
            throw GradleException("JDK 21 or higher is required to build registration-service. Current version: $javaVersion")
        }
    }
}

dependencies {
    // Import the pipeline-bom to manage dependency versions
    implementation(platform(project(":bom:pipeline-bom")))

    // Quarkus dependencies
    implementation("io.quarkus:quarkus-grpc")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-vertx")
    // Consul config extension is managed by Quarkus BOM
    implementation("io.smallrye.reactive:smallrye-mutiny-vertx-consul-client")
    implementation("io.smallrye.stork:stork-service-discovery-consul")
    implementation("io.smallrye.stork:stork-service-registration-consul")
    implementation("io.quarkus:quarkus-smallrye-stork")
    
    

    // Project dependencies
    implementation(project(":grpc-stubs"))
    implementation(project(":libraries:consul-client"))
    implementation(project(":libraries:pipeline-api"))


    // Test dependencies
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
}


tasks.named("compileJava") {
    dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}

// Ensure test classes can find all dependencies
tasks.named<Test>("test") {
    // Use JUnit Platform
    useJUnitPlatform()
    
    // Ensure Quarkus test resources are available
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    
    // Set test profile
    systemProperty("quarkus.test.profile", "test")
    
    // Increase memory for tests
    jvmArgs("-Xmx1g", "-XX:MaxMetaspaceSize=512m")
    
    // Add test timeout
    systemProperty("quarkus.test.hang-detection-timeout", "60s")
}

// Ensure proper dependency resolution
configurations {
    testImplementation {
        // Ensure test dependencies are properly resolved
        extendsFrom(configurations.implementation.get())
    }
}
