package llmchat.agent.memory

import llmchat.agent.TokenCounter

/**
 * Work memory store: volatile in-memory list for the current task.
 * Cleared at session end; survives only within a running session.
 */
class WorkMemoryStore : MemoryStore {

    private val items = mutableListOf<MemoryItem>()

    override fun add(content: String): MemoryItem {
        val item = MemoryItem.create(content)
        items.add(item)
        return item
    }

    override fun list(): List<MemoryItem> = items.toList()

    override fun delete(id: String): Boolean = items.removeIf { it.id == id }

    override fun clear() = items.clear()

    override fun estimateTokens(): Int =
        items.sumOf { TokenCounter.estimate(it.content) }
}
