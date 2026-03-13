package mcppipeline

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val MAX_INPUT_CHARS = 8_000
private const val DEFAULT_MODEL = "google/gemini-2.0-flash-001"

class OpenRouterClient {
    private val apiKey: String? = System.getenv("OPENROUTER_API_KEY")
    private val model: String = System.getenv("PIPELINE_MODEL") ?: DEFAULT_MODEL

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun summarize(content: String, maxLength: Int = 500): SummarizeResult {
        val key = apiKey
            ?: throw IllegalStateException("OPENROUTER_API_KEY environment variable is not set")

        val truncated = if (content.length > MAX_INPUT_CHARS) {
            System.err.println("[MCP-PIPELINE] summarize: input truncated from ${content.length} to $MAX_INPUT_CHARS chars")
            content.take(MAX_INPUT_CHARS)
        } else {
            content
        }

        val requestBody = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "You are a summarization assistant. Produce concise, informative summaries.")
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "Summarize the following in at most $maxLength characters:\n\n$truncated")
                })
            })
            put("max_tokens", 1024)
        }

        val responseText: String = http.post("https://openrouter.ai/api/v1/chat/completions") {
            header("Authorization", "Bearer $key")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(requestBody))
        }.bodyAsText()

        val parsed = Json.parseToJsonElement(responseText).jsonObject
        val summary = parsed["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw RuntimeException("No summary content in OpenRouter response: $responseText")

        val usedModel = parsed["model"]?.jsonPrimitive?.contentOrNull ?: model

        return SummarizeResult(
            summary = summary,
            model = usedModel,
            inputLength = truncated.length,
            outputLength = summary.length
        )
    }

    fun close() = http.close()
}

data class SummarizeResult(
    val summary: String,
    val model: String,
    val inputLength: Int,
    val outputLength: Int
)
