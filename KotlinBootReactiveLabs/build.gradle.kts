plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("kapt") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
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

var akka_version = "2.7.0"
val swagger_version = "2.2.0"
val scala_version = "2.13"
val pekko_version = "1.1.2"
val pekko_r2dbc_version = "1.0.0"
val pekko_jdbc_version = "1.1.0"

val r2dbc_mysql = "1.2.0"

val jwt_version = "0.11.5"
val jackson_version = "2.15.2"

val commonModelVersion = "0.0.1-SNAPSHOT"

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

    // Db Drivers
    implementation("org.postgresql:postgresql")
    implementation("io.asyncer:r2dbc-mysql:$r2dbc_mysql")

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
    // 비헤이버 패턴을 이용한 TypedActor를 활용합니다.
    //implementation("org.apache.pekko:pekko-actor_$scalaVersion:$pekkoVersion")
    //implementation("org.apache.pekko:pekko-stream_$scalaVersion:$pekkoVersion")

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

    // RunTime Only
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.postgresql:r2dbc-postgresql:0.9.3.RELEASE")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")

    // Test Only
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")

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

