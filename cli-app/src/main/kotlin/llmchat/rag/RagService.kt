package llmchat.rag

import indexer.embedding.EmbeddingClient
import indexer.search.ContextAssembler
import indexer.search.SearchService
import indexer.store.SqliteVectorStore
import java.io.File

data class RagResult(
    val augmentedMessage: String,
    val sources: List<String>,
    val chunksFound: Int,
    val rewrittenQuery: String? = null,
    val initialChunksFound: Int = chunksFound,
    val filteredCount: Int = 0,
    /** True when the top-ranked chunk score is below the relevance threshold — triggers "I don't know" mode. */
    val lowRelevance: Boolean = false,
    /** Cosine similarity score of the best reranked chunk (0.0–1.0). */
    val topScore: Float = 0f,
    /** Short chunk IDs (first 8 chars of UUID) for each source chunk, in order. */
    val chunkIds: List<String> = emptyList()
)

interface RagPipeline : AutoCloseable {
    suspend fun augment(query: String): RagResult
}

class RagService(
    private val searchService: SearchService,
    private val embeddingClient: EmbeddingClient,
    private val vectorStore: SqliteVectorStore,
    private val topK: Int = 5
) : RagPipeline {

    override suspend fun augment(query: String): RagResult {
        val entries = searchService.findSimilar(query, topK)
        if (entries.isEmpty()) {
            return RagResult(augmentedMessage = query, sources = emptyList(), chunksFound = 0)
        }
        val context = ContextAssembler.assemble(entries)
        val sources = ContextAssembler.summarizeSources(entries)
        val augmented = buildRagPrompt(context, query)
        return RagResult(augmented, sources, entries.size)
    }

    override fun close() {
        embeddingClient.close()
        vectorStore.close()
    }

    companion object {
        private const val DEFAULT_DB_PATH = "/.llmchat/knowledge-base.db"

        fun create(
            embeddingClient: EmbeddingClient,
            dbPath: String = System.getProperty("user.home") + DEFAULT_DB_PATH,
            topK: Int = 5
        ): RagService? {
            if (!File(dbPath).exists()) return null
            val store = SqliteVectorStore(dbPath)
            val searchService = SearchService(embeddingClient, store)
            return RagService(searchService, embeddingClient, store, topK)
        }
    }
}

internal fun buildRagPrompt(context: String, query: String): String = buildString {
    appendLine("Ты — точный ассистент, который отвечает СТРОГО на основе предоставленного контекста.")
    appendLine()
    appendLine("ПРАВИЛА:")
    appendLine("1. Используй ТОЛЬКО информацию из раздела «Контекст» ниже. Не добавляй знания из других источников.")
    appendLine("2. Для каждого утверждения ссылайся на ID фрагмента (указан как «[ID: ...]» в начале каждого блока).")
    appendLine("3. В конце ответа обязательно добавь два раздела:")
    appendLine("   ## Источники — список использованных файлов и ID фрагментов в формате: [ID: <id>] <filename> (<section>)")
    appendLine("   ## Цитаты — дословные (verbatim) цитаты из контекста, поддерживающие ответ")
    appendLine("4. Если ответ не содержится в контексте — напиши об этом явно.")
    appendLine()
    appendLine("Контекст:")
    appendLine(context)
    appendLine()
    append("Вопрос: $query")
}
