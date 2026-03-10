package llmchat.agent.task

import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-backed storage for [TaskState]. Persists to [storageFile]
 * (default: ~/.llmchat/task-state.json). I/O errors are logged to stderr
 * and never propagate — matches the LongTermStore error-handling pattern.
 */
object TaskStateStorage {

    private val storageFile: File = File(System.getProperty("user.home"), ".llmchat/task-state.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun save(state: TaskState) {
        try {
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(json.encodeToString(TaskState.serializer(), state))
        } catch (e: Exception) {
            System.err.println("[TaskStateStorage] Failed to save task state: ${e.message}")
        }
    }

    fun load(): TaskState? {
        if (!storageFile.exists()) return null
        return try {
            val raw = storageFile.readText()
            if (raw.isBlank()) null
            else json.decodeFromString(TaskState.serializer(), raw)
        } catch (e: Exception) {
            System.err.println("[TaskStateStorage] Failed to load task state: ${e.message}")
            null
        }
    }

    fun clear() {
        try {
            storageFile.delete()
        } catch (e: Exception) {
            System.err.println("[TaskStateStorage] Failed to clear task state: ${e.message}")
        }
    }

    fun hasActiveState(): Boolean = storageFile.exists() && storageFile.length() > 0
}
