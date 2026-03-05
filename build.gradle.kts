import com.google.protobuf.gradle.id

plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.qanal"
version = "0.1.0-SNAPSHOT"
description = "Qanal Control Plane — transfer orchestration & management API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val grpcVersion       = "1.68.1"
val protobufVersion   = "3.25.5"
val mapstructVersion  = "1.6.3"
val lombokVersion     = "1.18.36"

dependencies {
    // ── Web / Virtual Threads ──────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ── Persistence ───────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // ── Redis (progress cache, rate limiting, quota cache) ────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // ── Security ──────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-security")

    // ── gRPC server (receives reports from Data Plane) ────────────────────
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    // ── Metrics ───────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ── xxHash64 checksums ────────────────────────────────────────────────
    implementation("net.openhft:zero-allocation-hashing:0.16")

    // ── Code generation ───────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    // Lombok + MapStruct ordering fix
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // ── Stripe billing ────────────────────────────────────────────────────
    implementation("com.stripe:stripe-java:26.3.0")

    // ── Retry ─────────────────────────────────────────────────────────────
    implementation("org.springframework.retry:spring-retry")

    // ── Tests ─────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ── Protobuf / gRPC code generation ──────────────────────────────────────────
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
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

sourceSets {
    main {
        java {
            srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/grpc"
            )
        }
    }
}

// ── Explicit main class (prevents Spring Boot plugin from guessing) ───────────
springBoot {
    mainClass.set("com.qanal.control.QanalControlPlaneApplication")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Ensure proto code is generated before compilation
tasks.named("compileJava") {
    dependsOn("generateProto")
}
