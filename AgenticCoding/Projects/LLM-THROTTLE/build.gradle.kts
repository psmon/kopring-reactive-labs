plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("kapt") version "2.0.0"
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
val kotest_version = "5.9.1"

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Actor System
    implementation(platform("org.apache.pekko:pekko-bom_$scala_version:$pekko_version"))
    
    // Typed Actor
    implementation("org.apache.pekko:pekko-actor-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scala_version:$pekko_version")
    
    // Actor Streams
    implementation("org.apache.pekko:pekko-stream_$scala_version:$pekko_version")
    
    // Actor Logging
    implementation("org.apache.pekko:pekko-slf4j_$scala_version:$pekko_version")
    
    // Actor TestKit
    testImplementation("org.apache.pekko:pekko-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-stream-testkit_$scala_version:$pekko_version")
    
    // Test Only
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Kotest
    testImplementation("io.kotest:kotest-runner-junit5:$kotest_version")
    testImplementation("io.kotest:kotest-assertions-core:$kotest_version")
    
    // MockK
    testImplementation("io.mockk:mockk:1.13.13")
    
    // Coroutine testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs = listOf("-Duser.timezone=Asia/Seoul")
}