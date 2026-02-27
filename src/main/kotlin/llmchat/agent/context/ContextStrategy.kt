package llmchat.agent.context

/**
 * Token breakdown for context statistics.
 *
 * @property primary   Tokens from the main message history (recent exchanges).
 * @property secondary Tokens from secondary context (facts in StickyFacts, 0 for others).
 */
data class ContextTokenStats(val primary: Int, val secondary: Int = 0)

/**
 * Strategy interface for conversation context management.
 *
 * Each implementation controls how conversation history is retained and
 * presented to the LLM inside the system prompt.
 *
 * The interface is intentionally minimal — strategies own all state related
 * to context (messages, facts, branches) and expose it only through
 * [buildContextBlock] and [displayHistory].
 */
interface ContextStrategy {

    /**
     * Record a completed exchange and perform any post-processing
     * (e.g. fact extraction for [StickyFactsStrategy]).
     *
     * The function is `suspend` so that strategies can make async LLM calls
     * without leaking the agent factory into the interface.
     */
    suspend fun addMessage(user: String, assistant: String)

    /**
     * Bulk-load past messages without triggering side effects.
     * Used for session restoration.
     */
    fun loadMessages(messages: List<Pair<String, String>>)

    /**
     * Build the context block to be appended to the base system prompt.
     * Returns an empty string when there is nothing to add yet.
     */
    fun buildContextBlock(): String

    /** Clear all retained context. */
    fun clearHistory()

    /** Estimate token usage of the current context. */
    fun estimateTokenStats(): ContextTokenStats

    /** Print the current context in a human-readable form. */
    fun displayHistory()
}
