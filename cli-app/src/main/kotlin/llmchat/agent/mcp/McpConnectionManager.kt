package llmchat.agent.mcp

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages a single active MCP server connection.
 *
 * Lifecycle:
 *   connect()    - spawn subprocess, perform MCP handshake, build ToolRegistry
 *   disconnect() - destroy subprocess, clear state
 *   destroy()    - called from JVM shutdown hook; same as disconnect but never throws
 *
 * Security: uses ProcessBuilder(List) not Runtime.exec(String) to prevent shell injection.
 * stderr is kept separate (redirectErrorStream=false) to avoid JSON-RPC parse errors.
 *
 * Push notifications:
 *   The server may emit "[NOTIFY] title\tdescription" lines on stderr.
 *   Register a handler via [setNotificationHandler] to receive them.
 *   The handler is called on a background IO thread.
 */
class McpConnectionManager {

    data class ConnectionInfo(
        val command: String,
        val args: List<String>,
    ) {
        val commandLine: String get() = (listOf(command) + args).joinToString(" ")

        /**
         * Returns a human-readable system-prompt hint for the LLM describing what MCP tools
         * are available and which paths/roots the server was started with.
         */
        fun buildPromptBlock(): String {
            val paths = args.filter { !it.startsWith("-") && !it.startsWith("@") && it.isNotBlank() }
            return buildString {
                appendLine("[MCP TOOLS AVAILABLE]")
                appendLine("You have access to tools from an MCP server.")
                appendLine("Server command: $commandLine")
                if (paths.isNotEmpty()) {
                    appendLine("Allowed roots: ${paths.joinToString(", ")}")
                    appendLine("Use these absolute paths (or subdirectories) when calling file-system tools.")
                }
                append("[/MCP TOOLS AVAILABLE]")
            }
        }
    }

    private var process: Process? = null
    private var registry: ToolRegistry? = null
    private var connectionInfo: ConnectionInfo? = null
    private var stderrJob: Job? = null
    private var notificationHandler: ((title: String, description: String) -> Unit)? = null

    val isConnected: Boolean get() = process?.isAlive == true

    fun getRegistry(): ToolRegistry? = registry

    fun getConnectionInfo(): ConnectionInfo? = connectionInfo

    /**
     * Register a callback to receive push notifications from the MCP server.
     * The server emits "[NOTIFY] title\tdescription" on stderr; this handler is called
     * for each such line on a background IO thread.
     */
    fun setNotificationHandler(handler: (title: String, description: String) -> Unit) {
        notificationHandler = handler
    }

    /**
     * Spawns [command] with [args], performs the MCP initialize handshake via stdio,
     * retrieves the tool list, and returns the populated [ToolRegistry].
     *
     * Also starts a background coroutine reading stderr for [NOTIFY] lines.
     *
     * Throws on process start failure or MCP protocol error.
     * Caller must catch and call [disconnect] on failure to clean up.
     */
    suspend fun connect(command: String, args: List<String>): ToolRegistry {
        destroyProcess()

        // Resolve relative filesystem paths to absolute so MCP servers (e.g. filesystem)
        // receive unambiguous roots, and the LLM can be told the exact allowed paths.
        val resolvedArgs = args.map { arg ->
            when {
                arg == "." -> File(".").canonicalPath
                arg.startsWith("./") -> File(arg).canonicalPath
                else -> arg
            }
        }

        val cmdList = listOf(command) + resolvedArgs
        val pb = ProcessBuilder(cmdList)
        pb.redirectErrorStream(false)
        val proc = pb.start()
        process = proc

        // Read stderr in the background — parse [NOTIFY] lines and fire the handler
        stderrJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    if (line.startsWith("[NOTIFY] ")) {
                        val payload = line.removePrefix("[NOTIFY] ")
                        val tab = payload.indexOf('\t')
                        val title = if (tab >= 0) payload.substring(0, tab) else payload
                        val description = if (tab >= 0) payload.substring(tab + 1) else ""
                        try {
                            notificationHandler?.invoke(title, description)
                        } catch (_: Exception) {
                            // Handler failed for this notification — continue reading
                        }
                    }
                }
            } catch (_: Exception) {
                // stderr stream closed (process exited)
            }
        }

        val transport = McpToolRegistryProvider.defaultStdioTransport(proc)
        val reg = McpToolRegistryProvider.fromTransport(transport)

        registry = reg
        connectionInfo = ConnectionInfo(command = command, args = resolvedArgs)
        return reg
    }

    fun disconnect() {
        destroyProcess()
        registry = null
        connectionInfo = null
    }

    /** Called from JVM shutdown hook - must never throw. */
    fun destroy() {
        try { destroyProcess() } catch (_: Exception) {}
    }

    private fun destroyProcess() {
        stderrJob?.cancel()
        stderrJob = null
        process?.destroy()
        process = null
    }
}
