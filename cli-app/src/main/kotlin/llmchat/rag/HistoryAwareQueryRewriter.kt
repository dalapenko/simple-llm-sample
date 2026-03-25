package llmchat.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rewrites the current user query into a self-contained, context-independent form
 * by incorporating the recent conversation history and the current task state.
 *
 * Unlike [QueryRewriter] which only applies HyDE expansion, this rewriter performs
 * **contextual query condensation** — resolving conversational co-references
 * (e.g., "tell me more about it", "how does that work?") into fully standalone
 * search queries that can be understood without prior context.
 *
 * This runs *before* the HyDE-based [QueryRewriter] in [ConversationalRagService],
 * producing a two-stage rewriting pipeline:
 *   raw query → contextual condensation → standalone query → HyDE expansion → vector query
 */
class HistoryAwareQueryRewriter(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String? = null
) : AutoCloseable {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Rewrites [query] into a standalone search query using conversation [history]
     * (list of user→assistant pairs, most recent last) and [taskState].
     *
     * Returns the original [query] unchanged if history is empty and task state has no goal
     * (no rewriting needed for the first turn).
     */
    suspend fun rewrite(
        query: String,
        history: List<Pair<String, String>>,
        taskState: TaskState
    ): String {
        if (history.isEmpty() && taskState.goal == null) return query
        return try {
            val response: ChatResponse = http.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                if (apiKey != null) header("Authorization", "Bearer $apiKey")
                setBody(
                    ChatRequest(
                        model = model,
                        messages = listOf(ChatMessage("user", buildPrompt(query, history, taskState)))
                    )
                )
            }.body()
            response.choices.firstOrNull()?.message?.content?.trim() ?: query
        } catch (_: Exception) {
            query
        }
    }

    override fun close() = http.close()

    private fun buildPrompt(
        query: String,
        history: List<Pair<String, String>>,
        taskState: TaskState
    ): String = buildString {
        appendLine("Ты — помощник, который перефразирует вопросы с учётом контекста диалога.")
        appendLine()
        appendLine("Задача: Перепиши ТЕКУЩИЙ ВОПРОС так, чтобы он стал полностью самостоятельным поисковым запросом.")
        appendLine("Результирующий запрос должен быть понятен БЕЗ знания предыдущего диалога.")
        appendLine("Разреши все местоимения (\"оно\", \"это\", \"там\", \"он\", \"она\") — замени их конкретными терминами из контекста.")
        appendLine()

        if (taskState.goal != null || taskState.constraints.isNotEmpty() || taskState.clarifiedTerms.isNotEmpty()) {
            appendLine("=== СОСТОЯНИЕ ЗАДАЧИ ===")
            taskState.goal?.let { appendLine("Цель пользователя: $it") }
            if (taskState.constraints.isNotEmpty()) {
                appendLine("Ограничения: ${taskState.constraints.joinToString("; ")}")
            }
            if (taskState.clarifiedTerms.isNotEmpty()) {
                val termsStr = taskState.clarifiedTerms.entries.joinToString("; ") { (k, v) -> "$k = $v" }
                appendLine("Уточнённые термины: $termsStr")
            }
            appendLine()
        }

        if (history.isNotEmpty()) {
            appendLine("=== ИСТОРИЯ ДИАЛОГА (последние ${history.size} обменов) ===")
            history.takeLast(4).forEachIndexed { i, (user, assistant) ->
                appendLine("[${i + 1}] Пользователь: $user")
                appendLine("[${i + 1}] Ассистент: ${assistant.take(300)}...")
            }
            appendLine()
        }

        appendLine("=== ТЕКУЩИЙ ВОПРОС ===")
        appendLine(query)
        appendLine()
        appendLine("ВАЖНО: Верни ТОЛЬКО переформулированный поисковый запрос — одну строку текста, без объяснений и кавычек.")
    }

    // ── Wire types ────────────────────────────────────────────────────────────────

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        @SerialName("max_tokens") val maxTokens: Int = 300
    )

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice>)

    @Serializable
    private data class Choice(val message: ChoiceMessage)

    @Serializable
    private data class ChoiceMessage(val content: String)
}
