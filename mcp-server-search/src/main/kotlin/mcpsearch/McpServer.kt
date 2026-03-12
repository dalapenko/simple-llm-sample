package mcpsearch

import io.ktor.client.plugins.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

@Serializable
private data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement = JsonNull,
    val method: String,
    val params: JsonElement? = null
)

class McpServer(
    input: InputStream = System.`in`,
    output: OutputStream = System.`out`,
    private val client: SearchClient = DuckDuckGoClient()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val reader = input.bufferedReader()
    private val writer = PrintWriter(BufferedWriter(OutputStreamWriter(output)), true)

    private val toolDefinitions = buildJsonArray {
        add(buildJsonObject {
            put("name", "search")
            put(
                "description",
                "Searches the web using DuckDuckGo Instant Answers and returns structured JSON results. " +
                        "Best for well-known topics, technologies, people, and concepts (e.g. 'Kotlin', 'JVM', 'Python'). " +
                        "Use this to gather data as the first step of a pipeline. " +
                        "Pass the returned JSON to the 'summarize' tool to condense the results."
            )
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Search query — a topic name or question")
                    })
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Maximum number of results to return (default: 5, max: 20)")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("query")) })
            })
        })
    }

    suspend fun run() {
        System.err.println("[MCP-SEARCH] Server started")
        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (line.isBlank()) continue
                System.err.println("[MCP-SEARCH] ← $line")
                val response = try {
                    val request = json.decodeFromString<JsonRpcRequest>(line)
                    dispatch(request)
                } catch (e: SerializationException) {
                    buildError(JsonNull, -32700, "Parse error: ${e.message}")
                } catch (e: Exception) {
                    System.err.println("[MCP-SEARCH] Unexpected error: ${e.message}")
                    buildError(JsonNull, -32603, "Internal error: ${e.message}")
                }
                if (response != null) {
                    val out = json.encodeToString(response)
                    System.err.println("[MCP-SEARCH] → $out")
                    writer.println(out)
                }
            }
        } finally {
            System.err.println("[MCP-SEARCH] Shutting down")
            client.close()
        }
    }

    private suspend fun dispatch(request: JsonRpcRequest): JsonElement? = when (request.method) {
        "initialize" -> handleInitialize(request.id)
        "tools/list" -> handleToolsList(request.id)
        "tools/call" -> handleToolCall(request.id, request.params?.jsonObject ?: JsonObject(emptyMap()))
        "ping" -> buildOk(request.id, JsonObject(emptyMap()))
        "notifications/initialized",
        "notifications/cancelled",
        "notifications/progress" -> null

        else -> buildError(request.id, -32601, "Method not found: ${request.method}")
    }

    private fun handleInitialize(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject { put("tools", JsonObject(emptyMap())) })
            put("serverInfo", buildJsonObject {
                put("name", "search-mcp")
                put("version", "1.0.0")
            })
        })

    private fun handleToolsList(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject { put("tools", toolDefinitions) })

    private suspend fun handleToolCall(id: JsonElement, params: JsonObject): JsonElement {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return buildError(id, -32602, "Missing required parameter: name")
        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        return try {
            val text = when (name) {
                "search" -> executeSearch(arguments)
                else -> return buildError(id, -32602, "Unknown tool: $name")
            }
            buildToolResult(id, text, isError = false)
        } catch (e: ClientRequestException) {
            buildToolResult(id, "HTTP ${e.response.status.value} error: ${e.message}", isError = true)
        } catch (e: IllegalArgumentException) {
            buildError(id, -32602, e.message ?: "Invalid parameters")
        } catch (e: Exception) {
            System.err.println("[MCP-SEARCH] Tool error: ${e.message}")
            buildToolResult(id, "Tool execution failed: ${e.message}", isError = true)
        }
    }

    private suspend fun executeSearch(args: JsonObject): String {
        val query = args["query"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("query is required")
        require(query.isNotBlank()) { "query must not be blank" }
        require(query.length <= 500) { "query must not exceed 500 characters" }

        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 5
        require(limit in 1..20) { "limit must be between 1 and 20" }

        val results = client.search(query, limit)
        return json.encodeToString(buildJsonObject { put("searchResults", results) })
    }

    private fun buildOk(id: JsonElement, result: JsonElement): JsonElement =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }

    private fun buildError(id: JsonElement, code: Int, message: String): JsonElement =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }

    private fun buildToolResult(id: JsonElement, text: String, isError: Boolean): JsonElement =
        buildOk(id, buildJsonObject {
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
            put("isError", isError)
        })
}
