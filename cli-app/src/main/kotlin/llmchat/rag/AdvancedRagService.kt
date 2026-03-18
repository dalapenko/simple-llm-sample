package llmchat.rag

import indexer.embedding.OpenRouterEmbeddingClient
import indexer.embedding.cosineSimilarity
import indexer.search.ContextAssembler
import indexer.store.SqliteVectorStore
import java.io.File

/**
 * Advanced RAG pipeline: Query Rewriting → Filtering → LLM Reranking → Generation.
 *
 * Compared to the basic [RagService], three extra stages are inserted:
 *
 * 1. **Query Rewriting** ([QueryRewriter]) — LLM transforms the raw user query into a
 *    search-optimized form using the HyDE technique before embedding.
 *
 * 2. **Similarity Filtering** — chunks whose cosine similarity with the query embedding
 *    falls below [similarityThreshold] are discarded from the context window.
 *
 * 3. **LLM Reranking** ([LlmJudgeReranker]) — LLM selects the [finalTopK] most relevant
 *    chunks from the filtered set, eliminating noise from the context.
 *
 * Flow:
 * ```
 * Raw Query → Rewrite(LLM) → Embed → Search(initialTopK=10)
 *          → CosineSimilarity → Filter(threshold) → LLM-Judge(finalTopK=3)
 *          → ContextAssemble → buildRagPrompt → LLM Answer
 * ```
 */
class AdvancedRagService(
    private val embeddingClient: OpenRouterEmbeddingClient,
    private val vectorStore: SqliteVectorStore,
    private val queryRewriter: QueryRewriter,
    private val reranker: LlmJudgeReranker,
    private val similarityThreshold: Double = 0.65,
    private val initialTopK: Int = 10,
    private val finalTopK: Int = 3
) : RagPipeline {

    override suspend fun augment(query: String): RagResult {
        // Stage 1: Rewrite the query using LLM
        val rewrittenQuery = queryRewriter.rewrite(query)

        // Stage 2: Embed the rewritten query and retrieve a wider candidate set
        val queryEmbedding = embeddingClient.embed(rewrittenQuery)
        val rawEntries = vectorStore.search(queryEmbedding, initialTopK)

        if (rawEntries.isEmpty()) {
            return RagResult(
                augmentedMessage = query,
                sources = emptyList(),
                chunksFound = 0,
                rewrittenQuery = rewrittenQuery,
                initialChunksFound = 0,
                filteredCount = 0
            )
        }

        // Stage 3: Score all retrieved entries and apply similarity threshold
        val scored = rawEntries.map { entry ->
            ScoredEntry(entry, cosineSimilarity(queryEmbedding, entry.embedding))
        }
        val aboveThreshold = scored.filter { it.score >= similarityThreshold }
        val filteredCount = scored.size - aboveThreshold.size

        // Safety: if threshold removes everything, keep the best ones anyway
        val candidates = aboveThreshold.ifEmpty { scored.take(finalTopK) }

        // Stage 4: LLM-as-judge selects the final top-K
        val reranked = reranker.rerank(query, candidates, finalTopK)

        // Stage 5: Assemble context from the final reranked entries
        val finalEntries = reranked.map { it.entry }
        val context = ContextAssembler.assemble(finalEntries)
        val sources = ContextAssembler.summarizeSources(finalEntries)
        val augmented = buildRagPrompt(context, query)

        return RagResult(
            augmentedMessage = augmented,
            sources = sources,
            chunksFound = finalEntries.size,
            rewrittenQuery = rewrittenQuery,
            initialChunksFound = rawEntries.size,
            filteredCount = filteredCount
        )
    }

    override fun close() {
        embeddingClient.close()
        queryRewriter.close()
        reranker.close()
    }

    companion object {
        private const val DEFAULT_DB_PATH = "/.llmchat/knowledge-base.db"

        fun create(
            apiKey: String,
            model: String,
            dbPath: String = System.getProperty("user.home") + DEFAULT_DB_PATH,
            similarityThreshold: Double = 0.65,
            initialTopK: Int = 10,
            finalTopK: Int = 3
        ): AdvancedRagService? {
            if (!File(dbPath).exists()) return null
            val embeddingClient = OpenRouterEmbeddingClient(apiKey)
            val store = SqliteVectorStore(dbPath)
            return AdvancedRagService(
                embeddingClient = embeddingClient,
                vectorStore = store,
                queryRewriter = QueryRewriter(apiKey, model),
                reranker = LlmJudgeReranker(apiKey, model),
                similarityThreshold = similarityThreshold,
                initialTopK = initialTopK,
                finalTopK = finalTopK
            )
        }
    }
}

/**
 * Apply similarity threshold filtering to a scored entry list.
 *
 * Returns a [Pair] of (candidates, filteredCount). If the threshold removes all
 * entries the best [fallbackK] by score are returned as a safety net — this
 * prevents the pipeline from returning no context for aggressive thresholds.
 */
internal fun applyThresholdFilter(
    scored: List<ScoredEntry>,
    threshold: Double,
    fallbackK: Int
): Pair<List<ScoredEntry>, Int> {
    val above = scored.filter { it.score >= threshold }
    val filteredCount = scored.size - above.size
    val candidates = above.ifEmpty { scored.take(fallbackK) }
    return candidates to filteredCount
}
