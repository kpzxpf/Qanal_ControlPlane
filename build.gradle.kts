import com.google.protobuf.gradle.id

plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.volzhin"
version = "0.0.1-SNAPSHOT"
description = "ControlPlane"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val grpcVersion = "1.68.1"
val protobufVersion = "4.28.3"

dependencies {
    // REST API (virtual threads: spring.threads.virtual.enabled=true in application.properties)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // PostgreSQL via Hibernate
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Flyway migrations
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Redis — transfer progress
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // API key authentication
    implementation("org.springframework.boot:spring-boot-starter-security")

    // gRPC server for Data Plane
    implementation("io.grpc:grpc-netty-shaded:${grpcVersion}")
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")
    implementation("io.grpc:grpc-stub:${grpcVersion}")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    // Metrics (requires spring-boot-starter-actuator to expose /actuator/prometheus)
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // xxHash64 checksums
    implementation("net.openhft:zero-allocation-hashing:0.16")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:redis")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${protobufVersion}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
