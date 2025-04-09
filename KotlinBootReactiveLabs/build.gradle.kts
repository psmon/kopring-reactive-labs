plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("kapt") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.sonarqube") version "6.0.1.5171"
}

sonar {
    properties {
        property("sonar.projectKey", "kotlin-reactive")
        property("sonar.projectName", "kotlin-reactive")
    }
}

group = "org.example"
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

var akka_version = "2.6.2"
val swagger_version = "2.2.0"
val scala_version = "2.13"
val pekko_version = "1.1.2"

val pekko_kafka_version = "1.1.0"

val pekko_r2dbc_version = "1.0.0"
val pekko_jdbc_version = "1.1.0"

val r2dbc_mysql = "1.2.0"
val r2dbc_postgresql = "0.9.3.RELEASE"

val jwt_version = "0.11.5"
val jackson_version = "2.15.2"

val commonModelVersion = "0.0.1-SNAPSHOT"

val kotestVersion="5.9.1"

dependencies {

    // External module
    implementation("org.example.labs:common-model:$commonModelVersion")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

    // Jackson or Utils
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    implementation("io.jsonwebtoken:jjwt-api:$jwt_version")
    implementation("io.jsonwebtoken:jjwt-impl:$jwt_version")
    implementation("io.jsonwebtoken:jjwt-jackson:$jwt_version")

    // RunTime Only
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("io.asyncer:r2dbc-mysql:$r2dbc_mysql")
    runtimeOnly("org.postgresql:r2dbc-postgresql:$r2dbc_postgresql")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")

    // Annotation processor
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    //for kotlin
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Logging
    implementation("ch.qos.logback:logback-classic")

    // Reactive Stream
    implementation("org.apache.kafka:kafka-streams")

    // Actor System
    implementation(platform("org.apache.pekko:pekko-bom_$scala_version:$pekko_version"))

    // Classic Actor
    // implementation("org.apache.pekko:pekko-actor_$scala_version:$pekko_version")
    // implementation("org.apache.pekko:pekko-cluster-tools_$scala_version:$pekko_version")

    // Actor Streams
    implementation("org.apache.pekko:pekko-stream_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-connectors-kafka_$scala_version:$pekko_kafka_version")
    implementation("org.apache.pekko:pekko-connectors-kafka-cluster-sharding_$scala_version:$pekko_kafka_version")


    // Typed Actor
    implementation("org.apache.pekko:pekko-actor-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scala_version:$pekko_version")

    // Actor Cluster
    implementation("org.apache.pekko:pekko-cluster-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_$scala_version:$pekko_version")

    // Actor Persistence
    implementation("org.apache.pekko:pekko-persistence-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-persistence-query_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-persistence-jdbc_$scala_version:$pekko_jdbc_version")
    implementation("org.apache.pekko:pekko-persistence-r2dbc_$scala_version:$pekko_r2dbc_version")


    // Actor Logging
    implementation("org.apache.pekko:pekko-slf4j_$scala_version:$pekko_version")

    // Actor TestKit
    testImplementation("org.apache.pekko:pekko-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-stream-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-persistence-testkit_$scala_version:$pekko_version")
    testImplementation("org.apache.pekko:pekko-multi-node-testkit_$scala_version:$pekko_version")

    // Test Only
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")

    //  - kotest (testCode 작성시 도움을 주는 라이브러리)
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    //  - mockk (mocking 라이브러리 - mockito 대체)
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")

    // Develop Only
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // swagger
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
        if (project.hasProperty("serverPort")) {
            args.add("-Dserver.port=${project.property("serverPort")}")
        }
        if (project.hasProperty("clusterConfig")) {
            args.add("-DCluster=${project.property("clusterConfig")}")
        }else {
            args.add("-DCluster=standalone")
        }

        if (args.isNotEmpty()) {
            jvmArgs = args
        }
    }
}
