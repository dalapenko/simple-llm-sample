package mcppipeline

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
    output: OutputStream = System.`out`
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val searchClient = DuckDuckGoClient()
    private val summarizeClient = OpenRouterClient()
    private val fileManager = FileManager()
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
        add(buildJsonObject {
            put("name", "summarize")
            put(
                "description",
                "Condenses any text or JSON content into a concise summary using an LLM (requires OPENROUTER_API_KEY). " +
                        "Use this after 'search' to process its results, or independently on any raw text. " +
                        "Pass the returned summary JSON to the 'save_to_file' tool to persist the result."
            )
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "Text or JSON content to summarize (e.g. output from the 'search' tool)")
                    })
                    put("maxLength", buildJsonObject {
                        put("type", "integer")
                        put("description", "Approximate maximum character length of the summary (default: 500)")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("content")) })
            })
        })
        add(buildJsonObject {
            put("name", "save_to_file")
            put(
                "description",
                "Saves any content to a file under ~/.llmchat/pipeline-results/. " +
                        "Use this as the final step of a pipeline to persist results from 'search' or 'summarize'. " +
                        "Only a plain filename is accepted (no path separators). " +
                        "Supported formats: json, txt, md."
            )
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("filename", buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            "Name of the file to create, e.g. 'results.json' or 'summary.md'. No slashes allowed."
                        )
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "Text or JSON content to write to the file")
                    })
                    put("format", buildJsonObject {
                        put("type", "string")
                        put("description", "File format hint: json, txt, or md (default: txt)")
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("filename"))
                    add(JsonPrimitive("content"))
                })
            })
        })
    }

    suspend fun run() {
        System.err.println("[MCP-PIPELINE] Server started (search + summarize + save_to_file)")
        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (line.isBlank()) continue
                System.err.println("[MCP-PIPELINE] ← $line")
                val response = try {
                    val request = json.decodeFromString<JsonRpcRequest>(line)
                    dispatch(request)
                } catch (e: SerializationException) {
                    buildError(JsonNull, -32700, "Parse error: ${e.message}")
                } catch (e: Exception) {
                    System.err.println("[MCP-PIPELINE] Unexpected error: ${e.message}")
                    buildError(JsonNull, -32603, "Internal error: ${e.message}")
                }
                if (response != null) {
                    val out = json.encodeToString(response)
                    System.err.println("[MCP-PIPELINE] → $out")
                    writer.println(out)
                }
            }
        } finally {
            System.err.println("[MCP-PIPELINE] Shutting down")
            searchClient.close()
            summarizeClient.close()
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
                put("name", "pipeline-mcp")
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
                "summarize" -> executeSummarize(arguments)
                "save_to_file" -> executeSaveToFile(arguments)
                else -> return buildError(id, -32602, "Unknown tool: $name")
            }
            buildToolResult(id, text, isError = false)
        } catch (e: ClientRequestException) {
            buildToolResult(id, "HTTP ${e.response.status.value} error: ${e.message}", isError = true)
        } catch (e: IllegalArgumentException) {
            buildError(id, -32602, e.message ?: "Invalid parameters")
        } catch (e: IllegalStateException) {
            buildToolResult(id, e.message ?: "Configuration error", isError = true)
        } catch (e: Exception) {
            System.err.println("[MCP-PIPELINE] Tool '$name' error: ${e.message}")
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

        val results = searchClient.search(query, limit)
        return json.encodeToString(buildJsonObject { put("searchResults", results) })
    }

    private suspend fun executeSummarize(args: JsonObject): String {
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("content is required")
        require(content.isNotBlank()) { "content must not be blank" }

        val maxLength = args["maxLength"]?.jsonPrimitive?.intOrNull ?: 500
        require(maxLength in 100..10_000) { "maxLength must be between 100 and 10000" }

        val result = summarizeClient.summarize(content, maxLength)
        return json.encodeToString(buildJsonObject {
            put("processedResults", buildJsonObject {
                put("summary", result.summary)
                put("metadata", buildJsonObject {
                    put("model", result.model)
                    put("inputLength", result.inputLength)
                    put("outputLength", result.outputLength)
                })
            })
        })
    }

    private fun executeSaveToFile(args: JsonObject): String {
        val filename = args["filename"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("filename is required")
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("content is required")
        val format = args["format"]?.jsonPrimitive?.contentOrNull ?: "txt"

        val result = fileManager.save(filename, content, format)
        return json.encodeToString(buildJsonObject {
            put("saveStatus", buildJsonObject {
                put("success", result.success)
                put("filePath", result.filePath)
                put("sizeBytes", result.sizeBytes)
                put("format", result.format)
                put("overwritten", result.overwritten)
                put("message", "Saved ${result.sizeBytes} bytes to ${result.filePath}")
            })
        })
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
