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
val kotestVersion = "5.9.1"

dependencies {
    // Kotlin Core
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    
    // Pekko Dependencies
    implementation("org.apache.pekko:pekko-actor-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-stream_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-slf4j_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scala_version:$pekko_version")
    
    // Reactive Streams
    implementation("io.projectreactor:reactor-core:3.6.0")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Test Dependencies
    testImplementation("org.apache.pekko:pekko-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-stream-testkit_$scala_version:$pekko_version")
    
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.projectreactor:reactor-test:3.6.0")
    
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}