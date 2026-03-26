package llmchat.cli

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads named [LocalProviderPreset] configurations.
 *
 * Resolution order for --preset <name>:
 * 1. Built-in presets defined in this object.
 * 2. ./presets/<name>.json  (project-local presets)
 * 3. ~/.llmchat/presets/<name>.json  (user-global presets)
 *
 * Custom JSON files must match the [LocalProviderPreset] schema.
 * Unknown fields are silently ignored.
 */
object PresetLoader {

    private val json = Json { ignoreUnknownKeys = true }

    private val builtIn: Map<String, LocalProviderPreset> = mapOf(
        "code-review" to LocalProviderPreset(
            name = "code-review",
            description = "Optimized for code review: temp=0.1, maxTokens=4096, ctx=32768, qwen2.5-coder:7b-q4_K_M",
            temperature = 0.1,
            maxTokens = 4096,
            contextLength = 32768,
            contextWindow = 6,
            localModel = "qwen2.5-coder:7b-q4_K_M",
            systemPrompt = CODE_REVIEW_PROMPT
        ),
        "baseline" to LocalProviderPreset(
            name = "baseline",
            description = "Baseline local config — uses all CLI defaults, no optimisation (for comparison)"
        )
    )

    val availableNames: List<String> = builtIn.keys.sorted()

    /**
     * Resolves a preset by name. Returns null if the name is not found anywhere.
     */
    fun load(name: String): LocalProviderPreset? =
        builtIn[name]
            ?: readFile(File("presets/$name.json"))
            ?: readFile(File(System.getProperty("user.home") + "/.llmchat/presets/$name.json"))

    private fun readFile(file: File): LocalProviderPreset? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<LocalProviderPreset>(file.readText()) }.getOrNull()
    }
}

// ─── Built-in system prompts ────────────────────────────────────────────────

private const val CODE_REVIEW_PROMPT = """You are a senior software engineer performing a focused code review.

## Your role
Identify real defects: bugs, security vulnerabilities, performance issues, and maintainability problems.
Do NOT summarise what the code does. Do NOT praise unless specifically asked.

## Output format
Group findings under these headings (omit empty sections):

### Critical
Issues that can cause crashes, data loss, or security breaches.

### High
Bugs or flaws with significant impact on correctness or security.

### Medium
Performance problems, fragile assumptions, or poor error handling.

### Low
Style deviations, minor readability issues, dead code.

For each finding use:
- **Title** — one-line summary
- Problem: what is wrong and why it matters
- Fix: concrete corrected code snippet in a fenced block

## Rules
- Be specific — cite file names and line numbers when available
- One finding per bullet; no vague comments like "consider refactoring"
- If you lack context, ask a single targeted clarifying question instead of guessing
- Use fenced code blocks with the correct language tag"""
