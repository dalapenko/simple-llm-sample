package llmchat.agent

import llmchat.agent.task.TaskTransitionProposal

data class RequestStatistics(
    val response: String,
    val inputTokens: Int,
    val windowTokens: Int,
    val summaryTokens: Int,
    val responseTokens: Int,
    val longTermTokens: Int = 0,
    val transitionProposal: TaskTransitionProposal? = null
) {
    val historyTokens: Int get() = windowTokens + summaryTokens + longTermTokens
    val totalTokens: Int get() = inputTokens + historyTokens + responseTokens
}
