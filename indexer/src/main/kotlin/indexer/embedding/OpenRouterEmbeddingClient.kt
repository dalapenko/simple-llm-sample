package indexer.embedding

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * EmbeddingClient backed by OpenRouter's OpenAI-compatible embeddings API.
 *
 * Uses `text-embedding-3-small` (1536 dimensions) by default.
 * The [httpClient] is owned and closed by this instance — call [close] when done.
 */
class OpenRouterEmbeddingClient(
    private val apiKey: String,
    private val model: String = "text-embedding-3-small"
) : EmbeddingClient {

    override val dimensions: Int = 1536

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            // ignoreUnknownKeys is mandatory — OpenRouter adds fields not in the OpenAI spec
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun embed(text: String): FloatArray {
        val response: EmbeddingResponse = http.post("https://openrouter.ai/api/v1/embeddings") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(EmbeddingRequest(model = model, input = listOf(text)))
        }.body()
        return response.data.first().embedding.toFloatArray()
    }

    fun close() = http.close()

    // ── Wire types ───────────────────────────────────────────────────────────────

    @Serializable
    private data class EmbeddingRequest(
        val model: String,
        val input: List<String>,
        @SerialName("encoding_format") val encodingFormat: String = "float"
    )

    @Serializable
    private data class EmbeddingResponse(
        val data: List<EmbeddingData>
    )

    @Serializable
    private data class EmbeddingData(
        val embedding: List<Float>,
        val index: Int
    )
}
