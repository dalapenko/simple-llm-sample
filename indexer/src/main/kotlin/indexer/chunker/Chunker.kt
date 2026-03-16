package indexer.chunker

import java.io.File
import java.util.*

/**
 * Metadata describing the origin and structure of a document chunk.
 *
 * @property chunkId    Unique identifier (UUID) for deduplication and storage keying.
 * @property sourcePath Absolute path of the source file.
 * @property title      File name without extension (primary label).
 * @property section    Header, function name, or structural unit the chunk belongs to (optional).
 * @property strategy   Name of the chunking strategy that produced this chunk.
 */
data class ChunkMetadata(
    val chunkId: String = UUID.randomUUID().toString(),
    val sourcePath: String,
    val title: String,
    val section: String? = null,
    val strategy: String
)

/**
 * A piece of document text with its associated metadata.
 */
data class Chunk(
    val metadata: ChunkMetadata,
    val content: String
) {
    val tokenEstimate: Int get() = maxOf(1, content.length / 4)
}

/**
 * Strategy interface for splitting document text into indexable chunks.
 */
interface Chunker {
    val strategyName: String
    fun chunk(content: String, sourceFile: File): List<Chunk>
}
