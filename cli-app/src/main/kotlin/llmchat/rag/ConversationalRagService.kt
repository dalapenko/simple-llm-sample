package llmchat.rag

import java.io.File
import kotlin.Double
import kotlin.Int
import kotlin.Pair
import kotlin.String
import kotlin.collections.ArrayDeque
import kotlin.collections.isNotEmpty
import kotlin.collections.removeFirst
import kotlin.collections.toList
import kotlin.sequences.toList
import kotlin.text.appendLine
import kotlin.text.buildString
import kotlin.text.clear
import kotlin.text.compareTo
import kotlin.text.isNotEmpty
import kotlin.text.toList
import kotlin.to
import kotlin.toList

/**
 * History-aware RAG pipeline (Phase 5: Conversational Memory).
 *
 * Extends the [AdvancedRagService] pipeline with two additional layers:
 *
 * 1. **Contextual Query Rewriting** ([HistoryAwareQueryRewriter]) — resolves
 *    conversational co-references and pronouns using the last [historyWindowSize]
 *    turns before the query reaches the vector store. This produces a standalone,
 *    context-independent search query.
 *
 * 2. **Task State Injection** ([TaskStateManager]) — the current session state
 *    (goal, constraints, clarified terms, updated knowledge) is prepended to the
 *    final RAG prompt, keeping the agent aligned with the user's objectives even
 *    as raw conversation history is compressed by the sliding window strategy.
 *
 * Full pipeline per turn:
 * ```
 * raw query
 *   → HistoryAwareQueryRewriter (resolve co-references using last N turns)
 *   → standalone query
 *   → AdvancedRagService (HyDE expansion → embed → search → threshold filter → LLM rerank)
 *   → RAG prompt + task state block
 *   → LLM response
 * ```
 *
 * After each turn, call [addToHistory] and then [taskStateManager.updateFromTurn]
 * to keep the internal state current.
 */
class ConversationalRagService(
    private val inner: AdvancedRagService,
    private val historyRewriter: HistoryAwareQueryRewriter,
    val taskStateManager: TaskStateManager,
    private val historyWindowSize: Int = 6
) : RagPipeline {

    private val history = ArrayDeque<Pair<String, String>>()

    /**
     * Adds a completed turn to the rolling history window.
     * Must be called AFTER the LLM response is received so the history stays accurate.
     */
    fun addToHistory(userMessage: String, assistantResponse: String) {
        history.addLast(userMessage to assistantResponse)
        if (history.size > historyWindowSize) history.removeFirst()
    }

    /**
     * Clears conversation history and resets the task state.
     */
    fun reset() {
        history.clear()
        taskStateManager.reset()
    }

    /** Number of turns currently in the sliding history window. */
    fun historySize(): Int = history.size

    override suspend fun augment(query: String): RagResult {
        // Stage 1: Contextual query rewriting using conversation history + task state
        val historySnapshot = history.toList()
        val contextualQuery = historyRewriter.rewrite(query, historySnapshot, taskStateManager.getState())

        // Stage 2: Full Advanced RAG pipeline on the context-resolved query
        // (AdvancedRagService internally applies HyDE expansion on top of contextualQuery)
        val result = inner.augment(contextualQuery)

        // Stage 3: Inject task state block into the prompt (prepended for max attention weight)
        val taskStateBlock = taskStateManager.toSystemPromptBlock()
        val augmented = if (taskStateBlock.isNotEmpty() && !result.lowRelevance) {
            buildConversationalRagPrompt(result.augmentedMessage, taskStateBlock)
        } else {
            result.augmentedMessage
        }

        return result.copy(augmentedMessage = augmented)
    }

    override fun close() {
        inner.close()
        historyRewriter.close()
        taskStateManager.close()
    }

    companion object {
        private const val DEFAULT_DB_PATH = "/.llmchat/knowledge-base.db"

        fun create(
            apiKey: String,
            model: String,
            dbPath: String = System.getProperty("user.home") + DEFAULT_DB_PATH,
            similarityThreshold: Double = 0.65,
            finalTopK: Int = 3,
            historyWindowSize: Int = 6
        ): ConversationalRagService? {
            if (!File(dbPath).exists()) return null
            val inner = AdvancedRagService.create(
                apiKey = apiKey,
                model = model,
                dbPath = dbPath,
                similarityThreshold = similarityThreshold,
                finalTopK = finalTopK
            ) ?: return null
            return ConversationalRagService(
                inner = inner,
                historyRewriter = HistoryAwareQueryRewriter(apiKey, model),
                taskStateManager = TaskStateManager(apiKey, model),
                historyWindowSize = historyWindowSize
            )
        }
    }
}

/**
 * Prepends the task state block to an existing RAG-augmented prompt so the LLM
 * sees the session context before the retrieved document chunks.
 */
private fun buildConversationalRagPrompt(ragPrompt: String, taskStateBlock: String): String = buildString {
    appendLine(taskStateBlock)
    appendLine("---")
    appendLine()
    append(ragPrompt)
}
