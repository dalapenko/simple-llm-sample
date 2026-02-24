package llmchat.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class ConversationEntry(val user: String, val assistant: String)

object ConversationStorage {

    private val json = Json { prettyPrint = true }
    private val storageDir = File(System.getProperty("user.home"), ".llmchat")
    private val historyFile = File(storageDir, "history.json")

    fun save(history: List<Pair<String, String>>) {
        storageDir.mkdirs()
        val entries = history.map { (u, a) -> ConversationEntry(u, a) }
        historyFile.writeText(json.encodeToString(entries))
    }

    fun load(): List<Pair<String, String>> {
        if (!historyFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<ConversationEntry>>(historyFile.readText())
                .map { it.user to it.assistant }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() {
        historyFile.delete()
    }

    fun size(): Int {
        val history = load()
        return history.size
    }

    fun hasHistory(): Boolean {
        if (!historyFile.exists()) return false
        val text = historyFile.readText().trim()
        return text.isNotBlank() && text != "[]"
    }
}
