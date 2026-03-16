package indexer.store

import indexer.chunker.Chunk

/**
 * A persisted document chunk paired with its embedding vector.
 */
data class IndexEntry(
    val chunk: Chunk,
    val embedding: FloatArray
)

/**
 * Aggregate statistics about the current state of a vector store index.
 *
 * @property totalChunks      Total number of indexed chunks.
 * @property avgChunkSize     Average content length in characters.
 * @property metadataDensity  Fraction of chunks that carry a non-null [section] value (0.0–1.0).
 */
data class StoreStats(
    val totalChunks: Int,
    val avgChunkSize: Double,
    val metadataDensity: Double
)

/**
 * Provider-agnostic interface for storing and querying indexed document chunks.
 */
interface VectorStore {
    /** Insert or replace a chunk+embedding pair keyed by [IndexEntry.chunk.metadata.chunkId]. */
    fun upsert(entry: IndexEntry)

    /** Return the [topK] entries most similar to [query] by cosine similarity. */
    fun search(query: FloatArray, topK: Int = 5): List<IndexEntry>

    /** Aggregate statistics about the current index. */
    fun stats(): StoreStats

    /** Remove all entries from the store. */
    fun clear()

    fun close()
}
