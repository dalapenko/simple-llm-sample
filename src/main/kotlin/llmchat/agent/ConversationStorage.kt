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
    private val summariesFile = File(storageDir, "summaries.json")

    fun save(history: List<Pair<String, String>>, summaries: List<ConversationSummary>) {
        storageDir.mkdirs()
        val entries = history.map { (u, a) -> ConversationEntry(u, a) }
        historyFile.writeText(json.encodeToString(entries))
        summariesFile.writeText(json.encodeToString(summaries))
    }

    fun loadRecentTurns(): List<Pair<String, String>> {
        if (!historyFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<ConversationEntry>>(historyFile.readText())
                .map { it.user to it.assistant }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadSummaries(): List<ConversationSummary> {
        if (!summariesFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<ConversationSummary>>(summariesFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() {
        historyFile.delete()
        summariesFile.delete()
    }

    fun size(): Int {
        return loadRecentTurns().size
    }

    fun hasHistory(): Boolean {
        val hasHistory = historyFile.exists() && historyFile.readText().trim().let { it.isNotBlank() && it != "[]" }
        val hasSummaries = summariesFile.exists() && summariesFile.readText().trim().let { it.isNotBlank() && it != "[]" }
        return hasHistory || hasSummaries
    }
}
