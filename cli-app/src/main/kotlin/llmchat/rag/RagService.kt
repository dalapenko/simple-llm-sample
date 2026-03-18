package llmchat.rag

import indexer.embedding.OpenRouterEmbeddingClient
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
    val filteredCount: Int = 0
)

interface RagPipeline : AutoCloseable {
    suspend fun augment(query: String): RagResult
}

class RagService(
    private val searchService: SearchService,
    private val embeddingClient: OpenRouterEmbeddingClient,
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
            apiKey: String,
            dbPath: String = System.getProperty("user.home") + DEFAULT_DB_PATH,
            topK: Int = 5
        ): RagService? {
            if (!File(dbPath).exists()) return null
            val embeddingClient = OpenRouterEmbeddingClient(apiKey)
            val store = SqliteVectorStore(dbPath)
            val searchService = SearchService(embeddingClient, store)
            return RagService(searchService, embeddingClient, store, topK)
        }
    }
}

internal fun buildRagPrompt(context: String, query: String): String =
    "Используй предоставленный контекст для ответа на вопрос. " +
            "Если в контексте нет ответа, так и скажи.\n\n" +
            "Контекст:\n$context\n\nВопрос: $query"
