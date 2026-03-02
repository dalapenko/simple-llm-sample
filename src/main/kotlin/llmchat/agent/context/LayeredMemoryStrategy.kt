package llmchat.agent.context

import llmchat.agent.TokenCounter
import llmchat.agent.memory.*

/**
 * Three-layer memory strategy.
 *
 * Layers:
 *  - [MemoryLayer.LONG_TERM]  — persistent knowledge, survives restarts (file-backed)
 *  - [MemoryLayer.WORK]       — current-task data, lives for the session
 *  - [MemoryLayer.SHORT_TERM] — manually pinned notes + the sliding conversation window
 *
 * Token stats:
 *  - primary   = conversation exchanges + short-term notes
 *  - secondary = work memory
 *  - tertiary  = long-term memory
 */
class LayeredMemoryStrategy(
    private val windowSize: Int = 10,
    private val shortTermStore: ShortTermStore = ShortTermStore(maxItems = windowSize),
    private val workStore: WorkMemoryStore = WorkMemoryStore(),
    private val longTermStore: LongTermStore = LongTermStore()
) : ContextStrategy {

    private val exchanges = ArrayDeque<Pair<String, String>>()

    // ── ContextStrategy interface ────────────────────────────────────────────────

    override suspend fun addMessage(user: String, assistant: String) {
        exchanges.addLast(user to assistant)
        while (exchanges.size > windowSize) exchanges.removeFirst()
    }

    override fun loadMessages(messages: List<Pair<String, String>>) {
        exchanges.clear()
        messages.takeLast(windowSize).forEach { exchanges.addLast(it) }
    }

    override fun buildContextBlock(): String {
        val sb = StringBuilder()

        val longTermItems = longTermStore.list()
        if (longTermItems.isNotEmpty()) {
            sb.appendLine("[LONG-TERM KNOWLEDGE]")
            longTermItems.forEach { sb.appendLine("• [${it.id}] ${it.content}") }
            sb.appendLine()
        }

        val workItems = workStore.list()
        if (workItems.isNotEmpty()) {
            sb.appendLine("[CURRENT TASK]")
            workItems.forEach { sb.appendLine("• [${it.id}] ${it.content}") }
            sb.appendLine()
        }

        val shortTermItems = shortTermStore.list()
        if (shortTermItems.isNotEmpty()) {
            sb.appendLine("[SHORT-TERM NOTES]")
            shortTermItems.forEach { sb.appendLine("• [${it.id}] ${it.content}") }
            sb.appendLine()
        }

        if (exchanges.isNotEmpty()) {
            sb.appendLine("[RECENT CONVERSATION] (last ${exchanges.size} of max $windowSize exchanges)")
            exchanges.forEach { (user, assistant) ->
                sb.appendLine("User: $user")
                sb.appendLine("Assistant: $assistant")
                sb.appendLine()
            }
            sb.append("Please continue the conversation naturally.")
        }

        return sb.toString().trimEnd()
    }

    override fun clearHistory() {
        exchanges.clear()
        shortTermStore.clear()
        workStore.clear()
        // Long-term is intentionally NOT cleared by /clear — use /memory clear long-term explicitly
    }

    override fun estimateTokenStats(): ContextTokenStats {
        val exchangeTokens = exchanges.sumOf { (u, a) ->
            TokenCounter.estimate(u) + TokenCounter.estimate(a)
        }
        return ContextTokenStats(
            primary = exchangeTokens + shortTermStore.estimateTokens(),
            secondary = workStore.estimateTokens(),
            tertiary = longTermStore.estimateTokens()
        )
    }

    override fun displayHistory() {
        if (exchanges.isEmpty()) {
            println("No conversation history yet.")
            return
        }
        println("\n=== Layered Memory — Conversation (${exchanges.size}/$windowSize exchanges) ===")
        exchanges.forEachIndexed { i, (user, assistant) ->
            println("\n[${i + 1}] You: $user")
            println("    Assistant: $assistant")
        }
        println("=======================================================================\n")
    }

    // ── Memory management (called from CLI /memory commands) ───────────────────

    fun addToLayer(layer: MemoryLayer, content: String): MemoryItem =
        storeFor(layer).add(content)

    fun listLayer(layer: MemoryLayer): List<MemoryItem> =
        storeFor(layer).list()

    fun deleteFromLayer(layer: MemoryLayer, id: String): Boolean =
        storeFor(layer).delete(id)

    fun clearLayer(layer: MemoryLayer) =
        storeFor(layer).clear()

    private fun storeFor(layer: MemoryLayer): MemoryStore = when (layer) {
        MemoryLayer.SHORT_TERM -> shortTermStore
        MemoryLayer.WORK -> workStore
        MemoryLayer.LONG_TERM -> longTermStore
    }
}
