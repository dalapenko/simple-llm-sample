plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.shadow)
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.mcp)
    implementation(libs.koog.agents.features.trace)
    implementation(libs.kotlinx.serialization.json)

    // TUI: colored output, animated spinner
    implementation(libs.mordant)
    // TUI: markdown rendering in terminal
    implementation(libs.mordant.markdown)

    // TUI: line editing, history, tab completion
    implementation(libs.jline)

    implementation(project(":indexer"))

    // HTTP client for QueryRewriter and LlmJudgeReranker (Advanced RAG)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
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
