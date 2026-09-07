import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    kotlin("jvm") version "1.6.+"
    kotlin("plugin.serialization") version "1.6.+"
    application
    id("com.github.ben-manes.versions") version "0.+"
    id("org.liquibase.gradle") version "2.+"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}
group = "com.tylerkindy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.+"
val postgresVersion = "42.+"
val exposedVersion = "0.+"
val kotestVersion = "4.+"
val testcontainersVersion = "1.+"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.+")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.zaxxer:HikariCP:3.+")
    implementation("ch.qos.logback:logback-classic:1.+")
    implementation("org.apache.commons:commons-csv:1.+")

    liquibaseRuntime("org.liquibase:liquibase-core:4.+")
    liquibaseRuntime("org.postgresql:postgresql:$postgresVersion")
    liquibaseRuntime("javax.xml.bind:jaxb-api:2.+")
    liquibaseRuntime("info.picocli:picocli:4.+")

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}

application {
    mainClass.set("com.tylerkindy.betrayal.MainKt")
}

liquibase {
    val url = System.getenv("JDBC_DATABASE_URL")

    activities.register("main") {
        arguments = mapOf(
            "changeLogFile" to "src/main/resources/migrations.sql",
            "url" to url
        )
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.register("stage") {
    dependsOn("installDist")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("standalone")
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}
