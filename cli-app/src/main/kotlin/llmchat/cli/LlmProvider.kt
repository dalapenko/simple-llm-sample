package llmchat.cli

enum class LlmProvider(val cliName: String, val displayName: String) {
    OPENROUTER("openrouter", "OpenRouter"),
    OLLAMA("ollama", "Ollama (local)");

    companion object {
        fun fromCliName(name: String): LlmProvider? = entries.find { it.cliName == name }
        val availableNames: List<String> = entries.map { it.cliName }
        val default: LlmProvider = OPENROUTER
    }
}
