package indexer.search

import indexer.embedding.EmbeddingClient
import indexer.store.IndexEntry
import indexer.store.VectorStore

class SearchService(
    private val embeddingClient: EmbeddingClient,
    private val vectorStore: VectorStore
) {
    suspend fun findSimilar(query: String, topK: Int = 5): List<IndexEntry> {
        val embedding = embeddingClient.embed(query)
        return vectorStore.search(embedding, topK)
    }
}
