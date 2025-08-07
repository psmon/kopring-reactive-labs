plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

val scala_version = "2.13"
val pekko_version = "1.1.5"
val jackson_version = "2.15.2"
val kotestVersion = "5.9.1"
val kafka_version = "3.5.1"

dependencies {
    // Kotlin Core
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Pekko Dependencies
    implementation("org.apache.pekko:pekko-actor-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-stream_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-slf4j_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scala_version:$pekko_version")
    
    // Pekko Kafka Connector
    implementation("org.apache.pekko:pekko-connectors-kafka_$scala_version:1.1.0")
    
    // Kafka clients
    implementation("org.apache.kafka:kafka-clients:$kafka_version")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson_version")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Test Dependencies
    testImplementation("org.apache.pekko:pekko-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-stream-testkit_$scala_version:$pekko_version")
    
    // Testcontainers for Kafka
    testImplementation("org.testcontainers:testcontainers:1.19.0")
    testImplementation("org.testcontainers:kafka:1.19.0")
    testImplementation("org.testcontainers:junit-jupiter:1.19.0")
    
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}