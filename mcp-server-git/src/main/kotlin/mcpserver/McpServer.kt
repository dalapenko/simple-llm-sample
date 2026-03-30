package mcpserver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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

// ── JSON-RPC 2.0 request model ────────────────────────────────────────────────

@Serializable
private data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement = JsonNull,
    val method: String,
    val params: JsonElement? = null
)

// ── Server ────────────────────────────────────────────────────────────────────

/**
 * MCP server that exposes git repository tools over stdio (JSON-RPC 2.0).
 *
 * All git commands are executed via ProcessBuilder (no shell injection).
 * The working directory is inherited from the parent process (the CLI),
 * so git commands operate on the correct repository.
 *
 * stdout: JSON-RPC only (one object per line)
 * stderr: diagnostic logs
 */
class McpServer(
    input: InputStream = System.`in`,
    output: OutputStream = System.`out`
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val reader = input.bufferedReader()
    private val writer = PrintWriter(BufferedWriter(OutputStreamWriter(output)), true)

    // ── Tool schema definitions ───────────────────────────────────────────────

    private val toolDefinitions = buildJsonArray {
        add(buildJsonObject {
            put("name", "git_branch")
            put("description", "Returns the name of the currently active git branch.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(emptyMap()))
            })
        })
        add(buildJsonObject {
            put("name", "git_status")
            put("description", "Returns the short git status (modified, staged, untracked files).")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(emptyMap()))
            })
        })
        add(buildJsonObject {
            put("name", "git_diff")
            put(
                "description",
                "Returns a summary of changes in the working tree compared to HEAD (git diff --stat HEAD)."
            )
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(emptyMap()))
            })
        })
        add(buildJsonObject {
            put("name", "git_log")
            put("description", "Returns the last N commit messages. Defaults to 10.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Number of commits to return (default 10, max 50)")
                    })
                })
            })
        })
        add(buildJsonObject {
            put("name", "list_project_files")
            put("description", "Lists tracked files in the git repository (top-level structure).")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject {
                        put("type", "string")
                        put("description", "Subdirectory to list (optional, defaults to repository root)")
                    })
                })
            })
        })
    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    suspend fun run() {
        System.err.println("[MCP-Git] Git MCP server started (workdir: ${System.getProperty("user.dir")})")

        while (true) {
            val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
            if (line.isBlank()) continue

            System.err.println("[MCP-Git] ← $line")

            val response = try {
                val request = json.decodeFromString<JsonRpcRequest>(line)
                dispatch(request)
            } catch (e: SerializationException) {
                buildError(JsonNull, -32700, "Parse error: ${e.message}")
            } catch (e: Exception) {
                System.err.println("[MCP-Git] Unexpected error: ${e.message}")
                buildError(JsonNull, -32603, "Internal error: ${e.message}")
            }

            if (response != null) {
                val out = json.encodeToString(response)
                System.err.println("[MCP-Git] → $out")
                writer.println(out)
            }
        }

        System.err.println("[MCP-Git] Server shutting down")
    }

    // ── Dispatcher ────────────────────────────────────────────────────────────

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

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handleInitialize(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {
                put("tools", JsonObject(emptyMap()))
            })
            put("serverInfo", buildJsonObject {
                put("name", "git-mcp")
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
                "git_branch" -> executeGitBranch()
                "git_status" -> executeGitStatus()
                "git_diff" -> executeGitDiff()
                "git_log" -> executeGitLog(arguments)
                "list_project_files" -> executeListFiles(arguments)
                else -> return buildError(id, -32602, "Unknown tool: $name")
            }
            buildToolResult(id, text, isError = false)
        } catch (e: IllegalArgumentException) {
            buildError(id, -32602, e.message ?: "Invalid parameters")
        } catch (e: Exception) {
            System.err.println("[MCP-Git] Tool error: ${e.message}")
            buildToolResult(id, "Tool execution failed: ${e.message}", isError = true)
        }
    }

    // ── Tool implementations ──────────────────────────────────────────────────

    private suspend fun executeGitBranch(): String {
        val result = runCommand("git", "branch", "--show-current")
        return if (result.output.isBlank()) {
            // Fallback for detached HEAD state
            val sha = runCommand("git", "rev-parse", "--short", "HEAD")
            "HEAD detached at ${sha.output.trim()}"
        } else {
            result.output.trim()
        }
    }

    private suspend fun executeGitStatus(): String {
        val result = runCommand("git", "status", "--short")
        return if (result.output.isBlank()) "Working tree clean." else result.output
    }

    private suspend fun executeGitDiff(): String {
        val result = runCommand("git", "diff", "--stat", "HEAD")
        return if (result.output.isBlank()) "No uncommitted changes." else result.output
    }

    private suspend fun executeGitLog(args: JsonObject): String {
        val limit = args["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 50) ?: 10
        val result = runCommand("git", "log", "--oneline", "-$limit")
        return if (result.output.isBlank()) "No commits found." else result.output
    }

    private suspend fun executeListFiles(args: JsonObject): String {
        val subPath = args["path"]?.jsonPrimitive?.contentOrNull?.trim()?.trimStart('/')
        return if (subPath.isNullOrBlank()) {
            runCommand("git", "ls-files", "--cached", "--others", "--exclude-standard").output
                .lines()
                .take(100)
                .joinToString("\n")
        } else {
            runCommand("git", "ls-files", "--cached", "--others", "--exclude-standard", subPath).output
                .lines()
                .take(100)
                .joinToString("\n")
        }
    }

    // ── Process runner ────────────────────────────────────────────────────────

    private data class CommandResult(val output: String, val exitCode: Int)

    private suspend fun runCommand(vararg cmd: String): CommandResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(cmd.toList())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            System.err.println("[MCP-Git] Command ${cmd.joinToString(" ")} exited with $exitCode: $output")
        }
        CommandResult(output, exitCode)
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
