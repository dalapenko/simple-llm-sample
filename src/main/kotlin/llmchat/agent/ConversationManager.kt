package llmchat.agent

import ai.koog.agents.core.agent.AIAgent
import llmchat.agent.context.ContextStrategy

/**
 * Orchestrates conversation turns: builds the enriched system prompt,
 * calls the LLM, delegates history bookkeeping to [strategy], and
 * returns per-request statistics.
 *
 * The manager is intentionally strategy-agnostic: it never inspects the
 * internal state of [strategy]; it only calls the interface methods.
 */
class ConversationManager(
    private val agentFactory: (systemPrompt: String) -> AIAgent<String, String>,
    val strategy: ContextStrategy
) {
    private var baseSystemPrompt: String =
        "You are a helpful assistant. Answer user questions concisely."

    suspend fun sendMessage(userMessage: String): ChatResult<RequestStatistics> {
        return try {
            val enrichedSystemPrompt = buildSystemPrompt()
            val agent = agentFactory(enrichedSystemPrompt)

            val inputTokens = TokenCounter.estimate(userMessage)
            val statsBeforeTurn = strategy.estimateTokenStats()

            val response = agent.run(userMessage)
            val responseTokens = TokenCounter.estimate(response)

            // Post-turn: add to history (may trigger async side-effects like fact extraction)
            strategy.addMessage(userMessage, response)

            // Persist sliding-window history for session restore
            if (strategy is llmchat.agent.context.SlidingWindowStrategy) {
                ConversationStorage.save(strategy.getMessages(), emptyList())
            }

            Result.success(
                RequestStatistics(
                    response = response,
                    inputTokens = inputTokens,
                    windowTokens = statsBeforeTurn.primary,
                    summaryTokens = statsBeforeTurn.secondary,
                    responseTokens = responseTokens
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildSystemPrompt(): String {
        val contextBlock = strategy.buildContextBlock()
        return if (contextBlock.isEmpty()) baseSystemPrompt
        else "$baseSystemPrompt\n\n$contextBlock"
    }

    fun setBaseSystemPrompt(prompt: String) {
        baseSystemPrompt = prompt
    }

    fun clearHistory() {
        strategy.clearHistory()
        ConversationStorage.clear()
    }

    fun loadInitialMessages(messages: List<Pair<String, String>>) {
        strategy.loadMessages(messages)
    }

    fun displayHistory() {
        strategy.displayHistory()
    }
}
