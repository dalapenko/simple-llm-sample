package llmchat.agent.invariant

import kotlinx.serialization.Serializable

@Serializable
data class Invariant(
    val id: String,
    val description: String,
    val category: InvariantCategory = InvariantCategory.GENERAL,
    val createdAt: Long = System.currentTimeMillis()
)
