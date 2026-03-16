package indexer.chunker

import java.io.File

/**
 * Splits text into fixed-size character windows with configurable overlap.
 *
 * Overlap ensures that concepts spanning a chunk boundary are represented in both
 * the preceding and following chunks, improving retrieval recall.
 *
 * @param chunkSize  Maximum characters per chunk (default: 500).
 * @param overlapPct Fraction of [chunkSize] to repeat across adjacent chunks (default: 0.12 = 12%).
 */
class FixedSizeChunker(
    private val chunkSize: Int = 500,
    private val overlapPct: Float = 0.12f
) : Chunker {

    override val strategyName = "fixed"

    override fun chunk(content: String, sourceFile: File): List<Chunk> {
        if (content.isBlank()) return emptyList()

        val overlap = (chunkSize * overlapPct).toInt().coerceAtLeast(0)
        val step = (chunkSize - overlap).coerceAtLeast(1)
        val title = sourceFile.nameWithoutExtension

        val chunks = mutableListOf<Chunk>()
        var start = 0
        var index = 1

        while (start < content.length) {
            val end = (start + chunkSize).coerceAtMost(content.length)
            val text = content.substring(start, end).trim()

            if (text.isNotBlank()) {
                chunks += Chunk(
                    metadata = ChunkMetadata(
                        sourcePath = sourceFile.absolutePath,
                        title = title,
                        section = "chunk-$index",
                        strategy = strategyName
                    ),
                    content = text
                )
                index++
            }

            start += step
        }

        return chunks
    }
}
