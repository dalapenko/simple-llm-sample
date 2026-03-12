package mcpsavefile

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
    private val fileManager = FileManager()
    private val reader = input.bufferedReader()
    private val writer = PrintWriter(BufferedWriter(OutputStreamWriter(output)), true)

    private val toolDefinitions = buildJsonArray {
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
        System.err.println("[MCP-SAVE-FILE] Server started")
        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (line.isBlank()) continue
                System.err.println("[MCP-SAVE-FILE] ← $line")
                val response = try {
                    val request = json.decodeFromString<JsonRpcRequest>(line)
                    dispatch(request)
                } catch (e: SerializationException) {
                    buildError(JsonNull, -32700, "Parse error: ${e.message}")
                } catch (e: Exception) {
                    System.err.println("[MCP-SAVE-FILE] Unexpected error: ${e.message}")
                    buildError(JsonNull, -32603, "Internal error: ${e.message}")
                }
                if (response != null) {
                    val out = json.encodeToString(response)
                    System.err.println("[MCP-SAVE-FILE] → $out")
                    writer.println(out)
                }
            }
        } finally {
            System.err.println("[MCP-SAVE-FILE] Shutting down")
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
                put("name", "save-file-mcp")
                put("version", "1.0.0")
            })
        })

    private fun handleToolsList(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject { put("tools", toolDefinitions) })

    private fun handleToolCall(id: JsonElement, params: JsonObject): JsonElement {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return buildError(id, -32602, "Missing required parameter: name")
        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        return try {
            val text = when (name) {
                "save_to_file" -> executeSaveToFile(arguments)
                else -> return buildError(id, -32602, "Unknown tool: $name")
            }
            buildToolResult(id, text, isError = false)
        } catch (e: IllegalArgumentException) {
            buildError(id, -32602, e.message ?: "Invalid parameters")
        } catch (e: Exception) {
            System.err.println("[MCP-SAVE-FILE] Tool error: ${e.message}")
            buildToolResult(id, "Tool execution failed: ${e.message}", isError = true)
        }
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
