package llmchat.agent.memory

interface MemoryStore {
    fun add(content: String): MemoryItem
    fun list(): List<MemoryItem>
    fun delete(id: String): Boolean
    fun clear()
    fun estimateTokens(): Int
}
