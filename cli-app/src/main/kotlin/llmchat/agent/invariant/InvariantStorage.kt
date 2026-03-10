package llmchat.agent.invariant

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import llmchat.agent.TokenCounter
import java.io.File
import java.util.*

/**
 * Persistent storage for project invariants — non-negotiable constraints the LLM
 * must never violate. Stored at [storageFile] (default: ~/.llmchat/invariants.json).
 *
 * Invariants are injected into the system prompt on every request, before
 * conversation history and profile blocks, ensuring they always take precedence.
 */
class InvariantStorage(
    private val storageFile: File = defaultStorageFile()
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val invariants = mutableListOf<Invariant>()

    init {
        load()
    }

    fun add(description: String, category: InvariantCategory = InvariantCategory.GENERAL): Invariant {
        val id = "INV-" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        val invariant = Invariant(id = id, description = description, category = category)
        invariants.add(invariant)
        persist()
        return invariant
    }

    fun list(): List<Invariant> = invariants.toList()

    fun remove(id: String): Boolean {
        val removed = invariants.removeIf { it.id.equals(id, ignoreCase = true) }
        if (removed) persist()
        return removed
    }

    fun clear() {
        invariants.clear()
        persist()
    }

    fun isEmpty(): Boolean = invariants.isEmpty()

    fun estimateTokens(): Int = invariants.sumOf { TokenCounter.estimate(it.description) }

    /**
     * Builds the invariant block injected into the LLM system prompt.
     * The block instructs the model to check every invariant before responding
     * and refuse with a structured message if any would be violated.
     */
    fun buildPromptBlock(): String {
        if (invariants.isEmpty()) return ""
        return buildString {
            appendLine("[PROJECT INVARIANTS — MANDATORY CONSTRAINTS]")
            appendLine("These rules are absolute and non-negotiable. They must never be violated.")
            appendLine("Before proposing any technical solution, code, or architecture, you MUST")
            appendLine("explicitly verify your response against every invariant listed below.")
            appendLine()
            appendLine("If your proposed solution conflicts with any invariant, you MUST refuse")
            appendLine("and respond with:")
            appendLine("  ⚠ INVARIANT VIOLATION: Cannot proceed — this conflicts with [<id>]")
            appendLine("  (<category>): <description>.")
            appendLine("  Please reconsider your approach or explicitly update the invariant.")
            appendLine()
            appendLine("INVARIANTS:")
            invariants.groupBy { it.category }.forEach { (cat, items) ->
                appendLine("  ${cat.displayName}:")
                items.forEach { inv ->
                    appendLine("    • [${inv.id}] ${inv.description}")
                }
            }
        }.trimEnd()
    }

    private fun load() {
        if (!storageFile.exists()) return
        try {
            val raw = storageFile.readText()
            if (raw.isBlank()) return
            val loaded = json.decodeFromString(ListSerializer(Invariant.serializer()), raw)
            invariants.addAll(loaded)
        } catch (e: Exception) {
            System.err.println("[InvariantStorage] Failed to load invariants: ${e.message}")
        }
    }

    private fun persist() {
        try {
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(json.encodeToString(ListSerializer(Invariant.serializer()), invariants))
        } catch (e: Exception) {
            System.err.println("[InvariantStorage] Failed to save invariants: ${e.message}")
        }
    }

    companion object {
        fun defaultStorageFile(): File =
            File(System.getProperty("user.home"), ".llmchat/invariants.json")
    }
}
