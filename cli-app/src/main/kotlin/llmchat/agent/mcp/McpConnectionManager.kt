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
 * Manages multiple active MCP server connections simultaneously.
 *
 * Each call to [connect] spawns a new subprocess and adds it to the pool.
 * The merged [ToolRegistry] from all active connections is available via [getMergedRegistry].
 *
 * Security: uses ProcessBuilder(List) to prevent shell injection.
 * stderr is kept separate (redirectErrorStream=false) to avoid JSON-RPC parse errors.
 */
class McpConnectionManager {

    data class ConnectionInfo(
        val command: String,
        val args: List<String>,
    ) {
        val commandLine: String get() = (listOf(command) + args).joinToString(" ")

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

    private data class ActiveConnection(
        val info: ConnectionInfo,
        val process: Process,
        val registry: ToolRegistry,
        val stderrJob: Job,
    )

    private val connections = mutableListOf<ActiveConnection>()
    private var notificationHandler: ((title: String, description: String) -> Unit)? = null

    val isConnected: Boolean get() = connections.any { it.process.isAlive }

    fun getMergedRegistry(): ToolRegistry =
        connections.fold(ToolRegistry.EMPTY) { acc, conn -> acc + conn.registry }

    fun getConnections(): List<ConnectionInfo> = connections.map { it.info }

    // Backward-compatible accessors
    fun getRegistry(): ToolRegistry? = if (isConnected) getMergedRegistry() else null

    fun setNotificationHandler(handler: (title: String, description: String) -> Unit) {
        notificationHandler = handler
    }

    /**
     * Spawns [command] with [args], performs MCP handshake, retrieves tools,
     * and **adds** this server to the active connection pool.
     *
     * Calling connect() again does NOT disconnect existing servers —
     * all servers remain active and their tools are merged.
     */
    suspend fun connect(command: String, args: List<String>): Pair<ConnectionInfo, ToolRegistry> {
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

        val stderrJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    if (line.startsWith("[NOTIFY] ")) {
                        val payload = line.removePrefix("[NOTIFY] ")
                        val tab = payload.indexOf('\t')
                        val title = if (tab >= 0) payload.substring(0, tab) else payload
                        val description = if (tab >= 0) payload.substring(tab + 1) else ""
                        try {
                            notificationHandler?.invoke(title, description)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        val transport = McpToolRegistryProvider.defaultStdioTransport(proc)
        val registry = McpToolRegistryProvider.fromTransport(transport)

        val info = ConnectionInfo(command = command, args = resolvedArgs)
        connections.add(ActiveConnection(info, proc, registry, stderrJob))
        return Pair(info, registry)
    }

    /** Disconnect all active MCP connections. */
    fun disconnect() {
        val snapshot = connections.toList()
        connections.clear()
        for (conn in snapshot) {
            conn.stderrJob.cancel()
            conn.process.destroy()
        }
    }

    /** Called from JVM shutdown hook — must never throw. */
    fun destroy() {
        try { disconnect() } catch (_: Exception) {}
    }
}
