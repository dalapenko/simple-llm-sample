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
 * Rewrites user queries into search-optimized forms using an LLM (HyDE approach).
 *
 * Techniques applied:
 * - Abbreviation expansion
 * - Pronoun resolution
 * - Technical synonym injection
 * - Hypothetical Document Embeddings: generate a short hypothetical answer whose
 *   vocabulary is likely to overlap with the actual indexed documents.
 *
 * Falls back to the original query on any network/parsing error.
 */
class QueryRewriter(
    private val apiKey: String,
    private val model: String = "google/gemini-flash-1.5"
) : AutoCloseable {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun rewrite(query: String): String {
        return try {
            val response: ChatResponse = http.post("https://openrouter.ai/api/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(ChatRequest(model = model, messages = listOf(ChatMessage("user", buildPrompt(query)))))
            }.body()
            response.choices.firstOrNull()?.message?.content?.trim() ?: query
        } catch (_: Exception) {
            query
        }
    }

    override fun close() = http.close()

    private fun buildPrompt(query: String): String = """
        Ты — помощник, улучшающий поисковые запросы по кодовой базе на Kotlin.

        Задача: Перепиши следующий вопрос так, чтобы он стал максимально эффективным
        поисковым запросом для поиска по векторной базе данных.

        Применяй следующие техники:
        1. Расширь аббревиатуры и технические сокращения
        2. Разреши местоимения — замени "он", "она", "оно", "это" на конкретные термины
        3. Добавь технические синонимы и смежные термины
        4. Метод HyDE: напиши краткий гипотетический ответ, включив ключевые термины,
           которые вероятно встретятся в исходном коде или документации

        ВАЖНО: Верни ТОЛЬКО улучшенный поисковый запрос — без объяснений, без кавычек,
        без заголовков. Одна строка текста.

        Исходный вопрос: $query
    """.trimIndent()

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
