package mcpserver

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * HTTP client for the JSONPlaceholder REST API.
 *
 * All calls are suspending — they integrate cleanly with the coroutine-based
 * MCP server loop without blocking any thread.
 */
class JsonPlaceholderClient {

    private val baseUrl = "https://jsonplaceholder.typicode.com"

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    /**
     * Returns all posts, optionally filtered by [userId].
     */
    suspend fun getPosts(userId: Int? = null): List<Post> =
        http.get("$baseUrl/posts") {
            if (userId != null) parameter("userId", userId)
        }.body()

    /**
     * Returns a single post by [postId].
     * @throws ClientRequestException with 404 status if the post does not exist.
     */
    suspend fun getPost(postId: Int): Post =
        http.get("$baseUrl/posts/$postId").body()

    /**
     * Returns all comments for the post identified by [postId].
     */
    suspend fun getComments(postId: Int): List<Comment> =
        http.get("$baseUrl/posts/$postId/comments").body()

    fun close() = http.close()
}
