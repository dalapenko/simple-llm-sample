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
 * Represents the evolving session-level task context extracted from the dialogue.
 *
 * @property goal             What the user is trying to achieve in this session.
 * @property constraints      Rules or limitations the user has mentioned (e.g., "use only Kotlin 1.9").
 * @property clarifiedTerms   Project-specific jargon or definitions fixed during the chat.
 * @property updatedKnowledge Facts the user corrected (e.g., "we moved to Postgres instead of SQLite").
 */
@Serializable
data class TaskState(
    val goal: String? = null,
    val constraints: List<String> = emptyList(),
    val clarifiedTerms: Map<String, String> = emptyMap(),
    val updatedKnowledge: List<String> = emptyList()
) {
    fun isEmpty(): Boolean =
        goal == null && constraints.isEmpty() && clarifiedTerms.isEmpty() && updatedKnowledge.isEmpty()
}

/**
 * Silently tracks the user's evolving task context across a multi-turn conversation.
 *
 * After each exchange, [updateFromTurn] calls an LLM to extract any new goal,
 * constraints, clarified terms, or updated knowledge, then merges them into
 * the current [TaskState].
 *
 * The state is injected into the system prompt before the next turn via
 * [toSystemPromptBlock], keeping the agent aligned with the user's objectives
 * even as conversation history is compressed by the sliding window strategy.
 */
class TaskStateManager(
    private val apiKey: String,
    private val model: String = "google/gemini-flash-1.5"
) : AutoCloseable {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private var state: TaskState = TaskState()

    fun getState(): TaskState = state

    fun reset() {
        state = TaskState()
    }

    /**
     * Silently calls the LLM to extract any new goal/constraints/terms/knowledge
     * from the latest exchange and merges them into [state].
     *
     * Designed to be called fire-and-forget (errors are swallowed) so that
     * failures in state extraction never block the main conversation loop.
     */
    suspend fun updateFromTurn(userMessage: String, assistantResponse: String) {
        try {
            val prompt = buildExtractionPrompt(userMessage, assistantResponse, state)
            val response: ChatResponse = http.post("https://openrouter.ai/api/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(ChatRequest(model = model, messages = listOf(ChatMessage("user", prompt))))
            }.body()
            val text = response.choices.firstOrNull()?.message?.content?.trim() ?: return
            val extracted = parseExtractedState(text) ?: return
            state = mergeStates(state, extracted)
        } catch (_: Exception) {
            // Silent failure — state extraction is best-effort
        }
    }

    /**
     * Returns a formatted system prompt block with the current task state.
     * Returns an empty string if the state has not been populated yet.
     */
    fun toSystemPromptBlock(): String {
        if (state.isEmpty()) return ""
        return buildString {
            appendLine("## Состояние задачи (Task State)")
            state.goal?.let { appendLine("**Цель:** $it") }
            if (state.constraints.isNotEmpty()) {
                appendLine("**Ограничения:**")
                state.constraints.forEach { appendLine("  - $it") }
            }
            if (state.clarifiedTerms.isNotEmpty()) {
                appendLine("**Уточнённые термины:**")
                state.clarifiedTerms.forEach { (k, v) -> appendLine("  - $k: $v") }
            }
            if (state.updatedKnowledge.isNotEmpty()) {
                appendLine("**Обновлённые знания:**")
                state.updatedKnowledge.forEach { appendLine("  - $it") }
            }
            appendLine()
            append("Придерживайся этой цели и ограничений во всех последующих ответах.")
        }
    }

    /**
     * Human-readable multi-line summary for the `/state` command.
     */
    fun toDisplayString(): String {
        if (state.isEmpty()) return "Состояние задачи пусто. Продолжайте диалог, чтобы заполнить его."
        return buildString {
            appendLine("=== Состояние задачи ===")
            appendLine("Цель: ${state.goal ?: "(не определена)"}")
            if (state.constraints.isNotEmpty()) {
                appendLine("Ограничения:")
                state.constraints.forEach { appendLine("  • $it") }
            } else {
                appendLine("Ограничения: нет")
            }
            if (state.clarifiedTerms.isNotEmpty()) {
                appendLine("Уточнённые термины:")
                state.clarifiedTerms.forEach { (k, v) -> appendLine("  • $k = $v") }
            }
            if (state.updatedKnowledge.isNotEmpty()) {
                appendLine("Обновлённые знания:")
                state.updatedKnowledge.forEach { appendLine("  • $it") }
            }
        }.trimEnd()
    }

    override fun close() = http.close()

    // ── Prompt construction ───────────────────────────────────────────────────────

    private fun buildExtractionPrompt(
        userMessage: String,
        assistantResponse: String,
        currentState: TaskState
    ): String = buildString {
        appendLine("Ты — аналитик диалога. Проанализируй следующий обмен сообщениями и извлеки структурированную информацию о задаче.")
        appendLine()
        appendLine("ТЕКУЩЕЕ СОСТОЯНИЕ ЗАДАЧИ:")
        appendLine("Цель: ${currentState.goal ?: "(не определена)"}")
        appendLine("Ограничения: ${currentState.constraints.joinToString("; ").ifEmpty { "нет" }}")
        appendLine(
            "Уточнённые термины: ${
                currentState.clarifiedTerms.entries.joinToString("; ") { "${it.key}=${it.value}" }.ifEmpty { "нет" }
            }"
        )
        appendLine("Обновлённые знания: ${currentState.updatedKnowledge.joinToString("; ").ifEmpty { "нет" }}")
        appendLine()
        appendLine("НОВЫЙ ОБМЕН:")
        appendLine("Пользователь: $userMessage")
        appendLine("Ассистент: ${assistantResponse.take(500)}")
        appendLine()
        appendLine("Извлеки ТОЛЬКО новую информацию (не дублируй уже известное из текущего состояния).")
        appendLine("Верни ответ в СТРОГО следующем формате (пустые поля — пропусти строку целиком):")
        appendLine()
        appendLine("GOAL: <цель пользователя одной фразой, или UNCHANGED если не изменилась>")
        appendLine("CONSTRAINT: <конкретное ограничение>")
        appendLine("CONSTRAINT: <ещё одно ограничение>")
        appendLine("TERM: <термин>=<определение>")
        appendLine("KNOWLEDGE: <исправленный факт>")
        appendLine()
        append("Верни ТОЛЬКО эти строки — без заголовков, без объяснений, без лишних слов.")
    }

    // ── Parsing ───────────────────────────────────────────────────────────────────

    private fun parseExtractedState(text: String): TaskState? {
        var goal: String? = null
        val constraints = mutableListOf<String>()
        val clarifiedTerms = mutableMapOf<String, String>()
        val updatedKnowledge = mutableListOf<String>()

        text.lines().forEach { line ->
            when {
                line.startsWith("GOAL:") -> {
                    val v = line.removePrefix("GOAL:").trim()
                    if (v.isNotEmpty() && v.uppercase() != "UNCHANGED") goal = v
                }
                line.startsWith("CONSTRAINT:") -> {
                    val v = line.removePrefix("CONSTRAINT:").trim()
                    if (v.isNotEmpty()) constraints.add(v)
                }
                line.startsWith("TERM:") -> {
                    val v = line.removePrefix("TERM:").trim()
                    val parts = v.split("=", limit = 2)
                    if (parts.size == 2) clarifiedTerms[parts[0].trim()] = parts[1].trim()
                }
                line.startsWith("KNOWLEDGE:") -> {
                    val v = line.removePrefix("KNOWLEDGE:").trim()
                    if (v.isNotEmpty()) updatedKnowledge.add(v)
                }
            }
        }

        if (goal == null && constraints.isEmpty() && clarifiedTerms.isEmpty() && updatedKnowledge.isEmpty()) return null
        return TaskState(goal, constraints, clarifiedTerms, updatedKnowledge)
    }

    private fun mergeStates(existing: TaskState, extracted: TaskState): TaskState = TaskState(
        goal = extracted.goal ?: existing.goal,
        constraints = (existing.constraints + extracted.constraints).distinct(),
        clarifiedTerms = existing.clarifiedTerms + extracted.clarifiedTerms,
        updatedKnowledge = (existing.updatedKnowledge + extracted.updatedKnowledge).distinct()
    )

    // ── Wire types ────────────────────────────────────────────────────────────────

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        @SerialName("max_tokens") val maxTokens: Int = 500
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
