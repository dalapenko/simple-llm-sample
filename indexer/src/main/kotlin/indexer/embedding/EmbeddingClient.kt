package indexer.embedding

/**
 * Provider-agnostic interface for generating fixed-dimension text embeddings.
 */
interface EmbeddingClient {
    /** Generates an embedding vector for the given [text]. */
    suspend fun embed(text: String): FloatArray

    /** Dimensionality of the returned vectors. */
    val dimensions: Int
}

/**
 * Computes cosine similarity between two embedding vectors.
 * Returns a value in [-1.0, 1.0], where 1.0 = identical direction.
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vector dimensions must match: ${a.size} vs ${b.size}" }
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = Math.sqrt((normA * normB).toDouble()).toFloat()
    return if (denom == 0f) 0f else dot / denom
}
