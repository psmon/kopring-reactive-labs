plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("kapt") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

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
val pekko_r2dbc_version = "1.1.0-M1"
val jackson_version = "2.15.2"
val r2dbc_postgresql = "1.0.0.RELEASE"
val kotlinx_serialization_version = "1.6.3"

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson_version")
    
    // Pekko Actor System
    implementation(platform("org.apache.pekko:pekko-bom_$scala_version:$pekko_version"))
    implementation("org.apache.pekko:pekko-actor-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scala_version:$pekko_version")
    
    // Pekko Persistence - Event Sourcing
    implementation("org.apache.pekko:pekko-persistence-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-persistence-r2dbc_$scala_version:$pekko_r2dbc_version")
    implementation("org.apache.pekko:pekko-persistence-query_$scala_version:$pekko_version")
    
    // R2DBC PostgreSQL
    implementation("org.postgresql:r2dbc-postgresql:$r2dbc_postgresql")
    implementation("io.r2dbc:r2dbc-pool:1.0.1.RELEASE")
    implementation("org.postgresql:postgresql:42.6.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("org.apache.pekko:pekko-slf4j_$scala_version:$pekko_version")
    
    // Test
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-persistence-testkit_$scala_version:$pekko_version")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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