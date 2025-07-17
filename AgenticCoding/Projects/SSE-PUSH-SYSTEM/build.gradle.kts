plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("kapt") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val swagger_version = "2.2.0"
val scala_version = "2.13"
val pekko_version = "1.1.2"
val jackson_version = "2.15.2"
val kotestVersion = "5.9.1"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    
    // Kotlin
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Logging
    implementation("ch.qos.logback:logback-classic")
    
    // Actor System
    implementation(platform("org.apache.pekko:pekko-bom_$scala_version:$pekko_version"))
    implementation("org.apache.pekko:pekko-actor-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-cluster-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-persistence-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-persistence-query_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-slf4j_$scala_version:$pekko_version")
    
    // Database drivers (in-memory H2 for simplicity)
    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.r2dbc:r2dbc-h2")
    
    // Annotation processor
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Actor TestKit
    testImplementation("org.apache.pekko:pekko-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-persistence-testkit_$scala_version:$pekko_version")
    
    // Kotest
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    
    // Mock
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    
    // Development
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    
    // Swagger/OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:$swagger_version")
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

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    doFirst {
        val args = mutableListOf<String>()
        args.add("-DCluster=standalone")
        jvmArgs = args
    }
}