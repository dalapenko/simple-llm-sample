package llmchat.rag

import indexer.embedding.EmbeddingClient
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
/**
 * Minimum cosine similarity score of the top reranked chunk below which the pipeline
 * refuses to answer and triggers the "I don't know" anti-hallucination guardrail.
 */
/**
 * Calibrated for text-embedding-3-small on this KB: relevant code/docs score ~0.35–0.50,
 * fully off-topic queries score ~0.25–0.33.  Use 0.35 as the cutoff.
 */
private const val RELEVANCE_THRESHOLD = 0.35f

private const val LOW_RELEVANCE_MESSAGE =
    "Я не знаю ответа на этот вопрос на основе имеющихся документов. " +
            "Пожалуйста, уточните запрос или добавьте новые данные в базу знаний."

class AdvancedRagService(
    private val embeddingClient: EmbeddingClient,
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

        // Stage 2: Embed the rewritten query — gracefully degrade on API errors
        val queryEmbedding = try {
            embeddingClient.embed(rewrittenQuery)
        } catch (e: Exception) {
            return RagResult(
                augmentedMessage = LOW_RELEVANCE_MESSAGE,
                sources = emptyList(),
                chunksFound = 0,
                rewrittenQuery = rewrittenQuery,
                initialChunksFound = 0,
                filteredCount = 0,
                lowRelevance = true,
                topScore = 0f
            )
        }
        val rawEntries = vectorStore.search(queryEmbedding, initialTopK)

        if (rawEntries.isEmpty()) {
            return RagResult(
                augmentedMessage = LOW_RELEVANCE_MESSAGE,
                sources = emptyList(),
                chunksFound = 0,
                rewrittenQuery = rewrittenQuery,
                initialChunksFound = 0,
                filteredCount = 0,
                lowRelevance = true,
                topScore = 0f
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

        // Stage 5: Anti-hallucination guardrail — check top reranked score
        val topScore = reranked.firstOrNull()?.score ?: 0f
        if (topScore < RELEVANCE_THRESHOLD) {
            return RagResult(
                augmentedMessage = LOW_RELEVANCE_MESSAGE,
                sources = emptyList(),
                chunksFound = 0,
                rewrittenQuery = rewrittenQuery,
                initialChunksFound = rawEntries.size,
                filteredCount = filteredCount,
                lowRelevance = true,
                topScore = topScore
            )
        }

        // Stage 6: Assemble context with chunk IDs for citation support
        val finalEntries = reranked.map { it.entry }
        val context = ContextAssembler.assembleWithIds(finalEntries)
        val sources = ContextAssembler.summarizeSources(finalEntries)
        val chunkIds = ContextAssembler.extractChunkIds(finalEntries)
        val augmented = buildRagPrompt(context, query)

        return RagResult(
            augmentedMessage = augmented,
            sources = sources,
            chunksFound = finalEntries.size,
            rewrittenQuery = rewrittenQuery,
            initialChunksFound = rawEntries.size,
            filteredCount = filteredCount,
            lowRelevance = false,
            topScore = topScore,
            chunkIds = chunkIds
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
            embeddingClient: EmbeddingClient,
            chatBaseUrl: String,
            model: String,
            apiKey: String? = null,
            dbPath: String = System.getProperty("user.home") + DEFAULT_DB_PATH,
            similarityThreshold: Double = 0.65,
            initialTopK: Int = 10,
            finalTopK: Int = 3
        ): AdvancedRagService? {
            if (!File(dbPath).exists()) return null
            val store = SqliteVectorStore(dbPath)
            return AdvancedRagService(
                embeddingClient = embeddingClient,
                vectorStore = store,
                queryRewriter = QueryRewriter(chatBaseUrl, model, apiKey),
                reranker = LlmJudgeReranker(chatBaseUrl, model, apiKey),
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
