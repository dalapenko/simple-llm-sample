package llmchat.agent.memory

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import llmchat.agent.TokenCounter
import java.io.File

/**
 * Long-term memory store: file-backed JSON, survives application restarts.
 * Stored at [storageFile] (default: ~/.llmchat/long-term-memory.json).
 * I/O errors are logged to stderr but never propagate — the store degrades
 * gracefully to in-memory-only when the file system is unavailable.
 */
class LongTermStore(
    private val storageFile: File = defaultStorageFile()
) : MemoryStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val items = mutableListOf<MemoryItem>()

    init {
        load()
    }

    override fun add(content: String): MemoryItem {
        val item = MemoryItem.create(content)
        items.add(item)
        persist()
        return item
    }

    override fun list(): List<MemoryItem> = items.toList()

    override fun delete(id: String): Boolean {
        val removed = items.removeIf { it.id == id }
        if (removed) persist()
        return removed
    }

    override fun clear() {
        items.clear()
        persist()
    }

    override fun estimateTokens(): Int =
        items.sumOf { TokenCounter.estimate(it.content) }

    private fun load() {
        if (!storageFile.exists()) return
        try {
            val raw = storageFile.readText()
            if (raw.isBlank()) return
            val loaded = json.decodeFromString(ListSerializer(MemoryItem.serializer()), raw)
            items.addAll(loaded)
        } catch (e: Exception) {
            System.err.println("[LongTermStore] Failed to load memory: ${e.message}")
        }
    }

    private fun persist() {
        try {
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(
                json.encodeToString(ListSerializer(MemoryItem.serializer()), items)
            )
        } catch (e: Exception) {
            System.err.println("[LongTermStore] Failed to persist memory: ${e.message}")
        }
    }

    companion object {
        fun defaultStorageFile(): File =
            File(System.getProperty("user.home"), ".llmchat/long-term-memory.json")
    }
}
