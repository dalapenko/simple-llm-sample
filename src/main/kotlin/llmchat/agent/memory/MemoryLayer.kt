package llmchat.agent.memory

enum class MemoryLayer(val cliName: String, val displayName: String) {
    SHORT_TERM("short-term", "Short-Term"),
    WORK("work", "Work Memory"),
    LONG_TERM("long-term", "Long-Term");

    companion object {
        fun fromCliName(name: String): MemoryLayer? = entries.find { it.cliName == name }
    }
}
