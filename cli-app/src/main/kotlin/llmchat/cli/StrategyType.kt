package llmchat.cli

/**
 * Available context management strategies selectable via --strategy.
 */
enum class StrategyType(val cliName: String, val displayName: String) {
    SLIDING_WINDOW("sliding-window", "Sliding Window (drop oldest messages)"),
    STICKY_FACTS("sticky-facts", "Sticky Facts (key-value memory + recent window)"),
    BRANCHING("branching", "Branching (checkpoints & independent branches)"),
    LAYERED("layered", "Layered Memory (short-term / work / long-term)");

    companion object {
        val default = SLIDING_WINDOW

        fun fromCliName(name: String): StrategyType? =
            entries.find { it.cliName == name }

        val availableNames: List<String>
            get() = entries.map { it.cliName }
    }
}
