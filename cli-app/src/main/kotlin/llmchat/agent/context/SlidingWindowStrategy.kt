package llmchat.agent.context

import llmchat.agent.TokenCounter

/**
 * Sliding Window context strategy.
 *
 * Keeps only the last [windowSize] user-assistant exchanges in memory.
 * When the window is full, the oldest exchange is permanently dropped —
 * no summarisation, no compression.
 *
 * Trade-off: simple and deterministic, but older context is lost forever.
 *
 * @param windowSize Maximum number of exchanges to retain.
 */
class SlidingWindowStrategy(val windowSize: Int = 10) : ContextStrategy {

    private val messages = ArrayDeque<Pair<String, String>>()

    override suspend fun addMessage(user: String, assistant: String) {
        messages.addLast(user to assistant)
        while (messages.size > windowSize) {
            messages.removeFirst()
        }
    }

    override fun loadMessages(messages: List<Pair<String, String>>) {
        this.messages.clear()
        messages.takeLast(windowSize).forEach { this.messages.addLast(it) }
    }

    override fun buildContextBlock(): String {
        if (messages.isEmpty()) return ""

        val history = messages.joinToString("\n\n") { (user, assistant) ->
            "Previous exchange:\nUser: $user\nAssistant: $assistant"
        }
        return "Context from recent conversation (last ${messages.size} of max $windowSize exchanges):\n$history\n\nPlease continue the conversation naturally."
    }

    override fun clearHistory() {
        messages.clear()
    }

    override fun estimateTokenStats(): ContextTokenStats {
        val tokens = messages.sumOf { (u, a) ->
            TokenCounter.estimate(u) + TokenCounter.estimate(a)
        }
        return ContextTokenStats(primary = tokens)
    }

    override fun displayHistory() {
        if (messages.isEmpty()) {
            println("No conversation history yet.")
            return
        }
        println("\n=== Sliding Window History (${messages.size}/${windowSize} exchanges) ===")
        messages.forEachIndexed { i, (user, assistant) ->
            println("\n[${i + 1}] You: $user")
            println("    Assistant: $assistant")
        }
        println("=======================================================\n")
    }

    fun getMessages(): List<Pair<String, String>> = messages.toList()
}
