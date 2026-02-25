package llmchat.agent

/**
 * Result of a single LLM request, including estimated token usage.
 *
 * @property response The text response from the LLM
 * @property inputTokens Estimated tokens in the current user message
 * @property historyTokens Estimated tokens in all prior conversation turns
 * @property responseTokens Estimated tokens in the model response
 */
data class RequestStatistics(
    val response: String,
    val inputTokens: Int,
    val historyTokens: Int,
    val responseTokens: Int
) {
    val totalTokens: Int get() = inputTokens + historyTokens + responseTokens
}
