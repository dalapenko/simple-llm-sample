package llmchat.agent

/**
 * Result of a single LLM request.
 *
 * @property response The text response from the LLM
 */
data class RequestStatistics(
    val response: String
)
