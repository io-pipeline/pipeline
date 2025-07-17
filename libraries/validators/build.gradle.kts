plugins {
    `java-library`
    id("io.quarkus")
    id("org.kordamp.gradle.jandex") version "2.2.0-SNAPSHOT"
}

group = "com.pipeline"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(project(":bom")))
    implementation(project(":libraries:pipeline-api"))
    implementation(project(":libraries:pipeline-common"))
    implementation(project(":grpc-stubs"))
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("jakarta.annotation:jakarta.annotation-api")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}

tasks.named("compileJava") {
    dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}

tasks.named("compileTestJava") {
    dependsOn(tasks.named("jandex"))
}
