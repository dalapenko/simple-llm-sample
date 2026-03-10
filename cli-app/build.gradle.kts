plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.gradleup.shadow")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.koog:koog-agents:0.6.2")
    implementation("ai.koog:agents-mcp:0.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // TUI: colored output, animated spinner
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    // TUI: markdown rendering in terminal
    implementation("com.github.ajalt.mordant:mordant-markdown:3.0.2")

    // TUI: line editing, history, tab completion
    implementation("org.jline:jline:3.28.0")

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

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    mergeServiceFiles()
}
