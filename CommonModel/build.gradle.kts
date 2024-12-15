plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("kapt") version "2.0.0"
}

group = "org.example.labs"
version = "0.0.1-SNAPSHOT"
val jwtversion = "0.11.5"
val jacksonversion = "2.15.2"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("io.jsonwebtoken:jjwt-api:$jwtversion")
    implementation("io.jsonwebtoken:jjwt-impl:$jwtversion")
    implementation("io.jsonwebtoken:jjwt-jackson:$jwtversion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonversion")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}