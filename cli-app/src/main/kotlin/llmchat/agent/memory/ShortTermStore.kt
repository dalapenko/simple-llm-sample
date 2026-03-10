package llmchat.agent.memory

import llmchat.agent.TokenCounter

/**
 * Short-term memory store: a sliding window of manually added notes.
 * The oldest item is evicted when the window is full.
 */
class ShortTermStore(val maxItems: Int = 10) : MemoryStore {

    private val items = ArrayDeque<MemoryItem>()

    override fun add(content: String): MemoryItem {
        val item = MemoryItem.create(content)
        items.addLast(item)
        while (items.size > maxItems) items.removeFirst()
        return item
    }

    override fun list(): List<MemoryItem> = items.toList()

    override fun delete(id: String): Boolean = items.removeIf { it.id == id }

    override fun clear() = items.clear()

    override fun estimateTokens(): Int =
        items.sumOf { TokenCounter.estimate(it.content) }
}
