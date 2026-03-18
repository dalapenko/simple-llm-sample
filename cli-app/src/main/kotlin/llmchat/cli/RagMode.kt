package llmchat.cli

enum class RagMode(val cliName: String, val displayName: String) {
    BASIC("basic", "Базовый RAG"),
    ADVANCED("advanced", "Продвинутый RAG");

    companion object {
        fun fromCliName(name: String): RagMode? = entries.find { it.cliName == name }
        val availableNames: List<String> get() = entries.map { it.cliName }
    }
}
