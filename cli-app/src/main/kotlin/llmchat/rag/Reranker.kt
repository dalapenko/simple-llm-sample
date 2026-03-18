package llmchat.rag

import indexer.store.IndexEntry
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

/** An [IndexEntry] paired with its cosine similarity score (0.0–1.0). */
data class ScoredEntry(val entry: IndexEntry, val score: Float)

/** Post-retrieval reranking strategy. */
interface Reranker : AutoCloseable {
    /**
     * Select the best [topK] entries from [candidates].
     *
     * The returned list is ordered by descending relevance and contains at most [topK] items.
     */
    suspend fun rerank(query: String, candidates: List<ScoredEntry>, topK: Int): List<ScoredEntry>
}

/**
 * LLM-as-a-Judge reranker.
 *
 * Workflow:
 * 1. Build a prompt listing candidate chunks with similarity scores and content previews.
 * 2. Ask the LLM to return comma-separated 1-based indices in descending relevance order.
 * 3. Map indices to the original [ScoredEntry] list and take [topK].
 * 4. On parse failure, fall back to score-sorted top-K.
 */
class LlmJudgeReranker(
    private val apiKey: String,
    private val model: String = "google/gemini-flash-1.5"
) : Reranker {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun rerank(
        query: String,
        candidates: List<ScoredEntry>,
        topK: Int
    ): List<ScoredEntry> {
        if (candidates.isEmpty()) return emptyList()
        if (candidates.size <= topK) return candidates

        return try {
            val prompt = buildJudgePrompt(query, candidates)
            val response: ChatResponse = http.post("https://openrouter.ai/api/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(ChatRequest(model = model, messages = listOf(ChatMessage("user", prompt))))
            }.body()
            val raw = response.choices.firstOrNull()?.message?.content?.trim() ?: ""
            parseIndices(raw, candidates, topK)
        } catch (_: Exception) {
            candidates.take(topK)
        }
    }

    override fun close() = http.close()

    private fun buildJudgePrompt(query: String, candidates: List<ScoredEntry>): String {
        val chunks = candidates.mapIndexed { i, scored ->
            val meta = scored.entry.chunk.metadata
            val src = meta.title + if (meta.section != null) " / ${meta.section}" else ""
            val preview = scored.entry.chunk.content.take(400).replace("\n", " ").trim()
            "[${i + 1}] (сходство: ${"%.3f".format(scored.score)}) Источник: $src\n$preview"
        }.joinToString("\n\n")

        return """
            Ты — эксперт по ранжированию фрагментов документации и исходного кода.

            Вопрос пользователя: $query

            Ниже представлены ${candidates.size} фрагментов. Твоя задача — выбрать те,
            которые непосредственно отвечают на вопрос.

            $chunks

            Верни номера наиболее релевантных фрагментов через запятую, в порядке убывания
            релевантности. Выбери от 1 до ${candidates.size} фрагментов.

            Пример ответа: 3,1,7

            Ответ (только номера, без пояснений):
        """.trimIndent()
    }

    private fun parseIndices(raw: String, candidates: List<ScoredEntry>, topK: Int): List<ScoredEntry> {
        val indices = parseRankerResponse(raw, candidates.size, topK)
        return if (indices.isEmpty()) candidates.take(topK) else indices.map { candidates[it] }
    }

    // ── Wire types (private) ──────────────────────────────────────────────────────

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        @SerialName("max_tokens") val maxTokens: Int = 64
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

/**
 * Parse an LLM reranker response into 0-based indices into the candidate list.
 *
 * Accepts comma-separated 1-based integers; strips non-numeric characters,
 * deduplicates, and bounds-checks against [candidateCount].
 * Returns an empty list when the response cannot be parsed (signals fallback).
 */
internal fun parseRankerResponse(raw: String, candidateCount: Int, topK: Int): List<Int> =
    raw.replace(Regex("[^0-9,]"), "")
        .split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 1..candidateCount }
        .map { it - 1 }
        .distinct()
        .take(topK)
