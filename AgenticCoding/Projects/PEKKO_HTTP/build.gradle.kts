plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("kapt") version "2.0.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
val pekko_http_version = "1.1.0"
val jackson_version = "2.15.2"
val kotestVersion = "5.9.1"

dependencies {
    // Kotlin Core
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Pekko Dependencies
    implementation("org.apache.pekko:pekko-actor-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-stream_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-slf4j_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scala_version:$pekko_version")
    
    // Pekko HTTP
    implementation("org.apache.pekko:pekko-http_$scala_version:$pekko_http_version")
    implementation("org.apache.pekko:pekko-http-spray-json_$scala_version:$pekko_http_version")
    implementation("org.apache.pekko:pekko-http-cors_$scala_version:$pekko_http_version")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson_version")
    
    // Swagger/OpenAPI - Using older version compatible with Akka HTTP
    // Note: swagger-pekko-http might not exist yet, using alternative approach
    implementation("io.swagger.core.v3:swagger-annotations:2.2.20")
    implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Test Dependencies
    testImplementation("org.apache.pekko:pekko-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-stream-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-http-testkit_$scala_version:$pekko_http_version")
    
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
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

application {
    mainClass.set("com.example.pekkohttp.PekkoHttpServer")
}

// JVM 런타임 옵션 설정
tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dpekko.log-config-on-start=off"
    )
}

// Shadow JAR 설정
tasks.shadowJar {
    archiveBaseName.set("pekko-http-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.example.pekkohttp.PekkoHttpServer"
    }
}