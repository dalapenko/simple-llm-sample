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
 * EmbeddingClient backed by a local Ollama instance.
 *
 * Uses Ollama's native `/api/embed` endpoint.
 * The embedding [dimensions] are detected lazily on the first [embed] call
 * because each model has its own fixed dimension (e.g. nomic-embed-text = 768,
 * mxbai-embed-large = 1024, all-minilm = 384).
 */
class OllamaEmbeddingClient(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "nomic-embed-text"
) : EmbeddingClient {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // Lazily resolved — set on first successful embed call.
    private var _dimensions: Int = -1
    override val dimensions: Int get() = _dimensions

    override suspend fun embed(text: String): FloatArray {
        val response: EmbedResponse = http.post("$baseUrl/api/embed") {
            contentType(ContentType.Application.Json)
            setBody(EmbedRequest(model = model, input = text))
        }.body()
        val vector = response.embeddings.first().toFloatArray()
        if (_dimensions == -1) _dimensions = vector.size
        return vector
    }

    override fun close() = http.close()

    // ── Wire types ───────────────────────────────────────────────────────────────

    @Serializable
    private data class EmbedRequest(
        val model: String,
        val input: String
    )

    @Serializable
    private data class EmbedResponse(
        val embeddings: List<List<Float>>,
        @SerialName("total_duration") val totalDuration: Long? = null
    )
}
