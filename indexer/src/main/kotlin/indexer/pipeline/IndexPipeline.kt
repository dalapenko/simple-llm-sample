package indexer.pipeline

import indexer.chunker.Chunker
import indexer.chunker.FixedSizeChunker
import indexer.chunker.StructuralChunker
import indexer.document.DocumentLoader
import indexer.embedding.EmbeddingClient
import indexer.store.IndexEntry
import indexer.store.VectorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/**
 * Orchestrates the full document indexing pipeline:
 * **DocumentLoader → Chunker → EmbeddingClient → VectorStore**
 *
 * Files are loaded synchronously (I/O bound, fast), then chunked, then embedded
 * in parallel with a concurrency cap to respect API rate limits.
 *
 * @param maxConcurrentEmbeddings  Max simultaneous in-flight embedding API calls (default: 5).
 */
class IndexPipeline(
    private val loader: DocumentLoader,
    private val chunker: Chunker,
    private val embeddingClient: EmbeddingClient,
    private val store: VectorStore,
    private val maxConcurrentEmbeddings: Int = 5
) {

    /**
     * Index all supported files in [directory].
     *
     * @param withComparison  If true, also run the alternate strategy for a side-by-side report
     *                        (no embedding API calls for the comparison — chunk counting only).
     */
    suspend fun run(directory: File, withComparison: Boolean = false): IndexReport = coroutineScope {
        val startMs = System.currentTimeMillis()

        val documents = loader.loadDirectory(directory)
        if (documents.isEmpty()) {
            val empty = StrategyReport(chunker.strategyName, 0, 0.0, 0.0)
            return@coroutineScope IndexReport(
                directory = directory.absolutePath,
                filesProcessed = 0,
                primaryStrategy = empty,
                processingTimeMs = 0
            )
        }

        // Chunk with the primary strategy
        val chunks = documents.flatMap { doc -> chunker.chunk(doc.content, doc.file) }

        // Generate embeddings in parallel (rate-limited)
        val semaphore = Semaphore(maxConcurrentEmbeddings)
        chunks.map { chunk ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val embedding = embeddingClient.embed(chunk.content)
                    store.upsert(IndexEntry(chunk, embedding))
                }
            }
        }.awaitAll()

        val elapsed = System.currentTimeMillis() - startMs
        val stats = store.stats()

        val primaryReport = StrategyReport(
            strategyName = chunker.strategyName,
            totalChunks = stats.totalChunks,
            avgChunkSize = stats.avgChunkSize,
            metadataDensity = stats.metadataDensity
        )

        // Comparison: run the other strategy on the same docs (no embeddings needed)
        val comparisonReport = if (withComparison) buildComparisonReport(documents.map { it.file to it.content })
        else null

        IndexReport(
            directory = directory.absolutePath,
            filesProcessed = documents.size,
            primaryStrategy = primaryReport,
            comparisonStrategy = comparisonReport,
            processingTimeMs = elapsed
        )
    }

    // ── Comparison (chunk-only, no API calls) ───────────────────────────────────

    private fun buildComparisonReport(files: List<Pair<File, String>>): StrategyReport {
        val altChunker: Chunker = when (chunker) {
            is FixedSizeChunker -> StructuralChunker()
            is StructuralChunker -> FixedSizeChunker()
            else -> FixedSizeChunker()
        }
        val altChunks = files.flatMap { (file, content) -> altChunker.chunk(content, file) }
        val total = altChunks.size
        val avgSize = if (total == 0) 0.0 else altChunks.sumOf { it.content.length }.toDouble() / total
        val density = if (total == 0) 0.0 else altChunks.count { it.metadata.section != null }.toDouble() / total
        return StrategyReport(altChunker.strategyName, total, avgSize, density)
    }
}
