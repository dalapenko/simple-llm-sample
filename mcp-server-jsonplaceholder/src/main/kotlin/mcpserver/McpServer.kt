package mcpserver

import io.ktor.client.plugins.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

// ── JSON-RPC 2.0 request model ────────────────────────────────────────────────

/**
 * Incoming JSON-RPC 2.0 request.
 *
 * [id] defaults to [JsonNull] so that MCP notifications (which carry no id)
 * are parsed without error. The server never writes a response when the
 * original message was a notification.
 */
@Serializable
private data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement = JsonNull,
    val method: String,
    val params: JsonElement? = null
)

// ── Server ────────────────────────────────────────────────────────────────────

/**
 * MCP server that communicates over stdio using JSON-RPC 2.0.
 *
 * Design rules:
 * - stdout carries ONLY valid JSON-RPC objects, one per line.
 * - All debug, info, and error messages go to stderr.
 * - stdin is read with [Dispatchers.IO] so coroutine scheduling is not blocked.
 */
class McpServer(
    input: InputStream = System.`in`,
    output: OutputStream = System.`out`
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = JsonPlaceholderClient()
    private val reader = input.bufferedReader()
    private val writer = PrintWriter(BufferedWriter(OutputStreamWriter(output)), true)

    // ── Tool schema definitions ───────────────────────────────────────────────

    private val toolDefinitions = buildJsonArray {
        add(buildJsonObject {
            put("name", "get_posts")
            put("description", "Fetches a list of posts from JSONPlaceholder. Optionally filter by user ID.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("userId", buildJsonObject {
                        put("type", "integer")
                        put("description", "Filter posts by user ID (optional)")
                    })
                })
            })
        })
        add(buildJsonObject {
            put("name", "get_post_details")
            put("description", "Fetches a specific post by its ID.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("postId", buildJsonObject {
                        put("type", "integer")
                        put("description", "The ID of the post to fetch")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("postId")) })
            })
        })
        add(buildJsonObject {
            put("name", "get_comments")
            put("description", "Fetches all comments for a specific post.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("postId", buildJsonObject {
                        put("type", "integer")
                        put("description", "The ID of the post to fetch comments for")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("postId")) })
            })
        })
    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    /**
     * Runs the stdio communication loop.
     *
     * Reads newline-delimited JSON-RPC messages from stdin, dispatches them,
     * and writes responses to stdout. Blocks until stdin is closed (EOF).
     */
    suspend fun run() {
        System.err.println("[MCP] JSONPlaceholder MCP server started")

        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (line.isBlank()) continue

                System.err.println("[MCP] ← $line")

                val response = try {
                    val request = json.decodeFromString<JsonRpcRequest>(line)
                    dispatch(request)
                } catch (e: SerializationException) {
                    buildError(JsonNull, -32700, "Parse error: ${e.message}")
                } catch (e: Exception) {
                    System.err.println("[MCP] Unexpected error: ${e.message}")
                    buildError(JsonNull, -32603, "Internal error: ${e.message}")
                }

                if (response != null) {
                    val out = json.encodeToString(response)
                    System.err.println("[MCP] → $out")
                    writer.println(out)
                }
            }
        } finally {
            System.err.println("[MCP] Server shutting down")
            client.close()
        }
    }

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private suspend fun dispatch(request: JsonRpcRequest): JsonElement? = when (request.method) {
        "initialize"               -> handleInitialize(request.id)
        "tools/list"               -> handleToolsList(request.id)
        "tools/call"               -> handleToolCall(request.id, request.params?.jsonObject ?: JsonObject(emptyMap()))
        "ping"                     -> buildOk(request.id, JsonObject(emptyMap()))
        // Notifications — no response
        "notifications/initialized",
        "notifications/cancelled",
        "notifications/progress"   -> null
        else -> buildError(request.id, -32601, "Method not found: ${request.method}")
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handleInitialize(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {
                put("tools", JsonObject(emptyMap()))
            })
            put("serverInfo", buildJsonObject {
                put("name", "jsonplaceholder-mcp")
                put("version", "1.0.0")
            })
        })

    private fun handleToolsList(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject {
            put("tools", toolDefinitions)
        })

    private suspend fun handleToolCall(id: JsonElement, params: JsonObject): JsonElement {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return buildError(id, -32602, "Missing required parameter: name")
        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        return try {
            val text = when (name) {
                "get_posts"        -> executePosts(arguments)
                "get_post_details" -> executePostDetails(arguments)
                "get_comments"     -> executeComments(arguments)
                else               -> return buildError(id, -32602, "Unknown tool: $name")
            }
            buildToolResult(id, text, isError = false)
        } catch (e: ClientRequestException) {
            val status = e.response.status.value
            buildToolResult(id, "HTTP $status from JSONPlaceholder: ${e.message}", isError = true)
        } catch (e: IllegalArgumentException) {
            buildError(id, -32602, e.message ?: "Invalid parameters")
        } catch (e: Exception) {
            System.err.println("[MCP] Tool error: ${e.message}")
            buildToolResult(id, "Tool execution failed: ${e.message}", isError = true)
        }
    }

    // ── Tool implementations ──────────────────────────────────────────────────

    private suspend fun executePosts(args: JsonObject): String {
        val userId = args["userId"]?.jsonPrimitive?.intOrNull
        return json.encodeToString(client.getPosts(userId))
    }

    private suspend fun executePostDetails(args: JsonObject): String {
        val postId = args["postId"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("postId is required and must be an integer")
        return json.encodeToString(client.getPost(postId))
    }

    private suspend fun executeComments(args: JsonObject): String {
        val postId = args["postId"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("postId is required and must be an integer")
        return json.encodeToString(client.getComments(postId))
    }

    // ── Response builders ─────────────────────────────────────────────────────

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
