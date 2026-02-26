package llmchat.agent

data class RequestStatistics(
    val response: String,
    val inputTokens: Int,
    val windowTokens: Int,
    val summaryTokens: Int,
    val responseTokens: Int
) {
    val historyTokens: Int get() = windowTokens + summaryTokens
    val totalTokens: Int get() = inputTokens + historyTokens + responseTokens
}
