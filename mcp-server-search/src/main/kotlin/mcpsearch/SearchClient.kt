package mcpsearch

import kotlinx.serialization.json.JsonElement

interface SearchClient {
    suspend fun search(query: String, limit: Int = 5): JsonElement
    fun close()
}
