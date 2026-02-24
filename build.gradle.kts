plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.koog:koog-agents:0.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("llmchat.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "llmchat.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}