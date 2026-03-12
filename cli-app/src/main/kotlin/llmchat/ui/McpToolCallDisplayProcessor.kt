package llmchat.ui

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonPrimitive

/**
 * Intercepts Koog's Tracing events and displays MCP tool calls in the terminal.
 * Prints above the active spinner line so users can see exactly which MCP tools
 * the agent is delegating to.
 */
class McpToolCallDisplayProcessor(private val output: CliOutput) : FeatureMessageProcessor() {

    override val isOpen: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun processMessage(message: FeatureMessage) {
        when (message) {
            is ToolCallStartingEvent -> {
                val argsPreview = message.toolArgs.entries
                    .take(3)
                    .joinToString(", ") { (k, v) ->
                        val raw = (v as? JsonPrimitive)?.content ?: v.toString()
                        val preview = if (raw.length > 50) raw.take(50) + "…" else raw
                        "$k=\"$preview\""
                    }
                output.printMcpToolCall(message.toolName, argsPreview)
            }

            is ToolCallCompletedEvent -> {
                val resultPreview = message.result?.toString()?.let { r ->
                    if (r.length > 80) r.take(80) + "…" else r
                } ?: "done"
                output.printMcpToolResult(message.toolName, resultPreview)
            }
        }
    }

    override suspend fun close() {}
}
