package llmchat.agent.memory

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MemoryItem(
    val id: String,
    val content: String,
    val createdAt: Long
) {
    companion object {
        fun create(content: String) = MemoryItem(
            id = UUID.randomUUID().toString().take(8),
            content = content,
            createdAt = System.currentTimeMillis()
        )
    }
}
