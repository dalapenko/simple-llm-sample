package llmchat.agent

import kotlinx.serialization.Serializable

@Serializable
data class ConversationSummary(
    val content: String,
    val coveredTurnCount: Int,
    val estimatedTokens: Int
)
