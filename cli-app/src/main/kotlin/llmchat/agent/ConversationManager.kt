package llmchat.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import llmchat.agent.context.ContextStrategy
import llmchat.agent.invariant.InvariantStorage
import llmchat.agent.mcp.McpConnectionManager
import llmchat.agent.profile.ProfileManager
import llmchat.agent.task.TaskFSM
import llmchat.agent.task.TaskTransitionProposal

/**
 * Orchestrates conversation turns: builds the enriched system prompt,
 * calls the LLM, delegates history bookkeeping to [strategy], and
 * returns per-request statistics.
 *
 * The manager is intentionally strategy-agnostic: it never inspects the
 * internal state of [strategy]; it only calls the interface methods.
 *
 * In autonomous mode ([autoMode] = true) the manager parses
 * [TASK_TRANSITION]…[/TASK_TRANSITION] blocks emitted by the LLM,
 * strips them from the visible response, and surfaces them as
 * [RequestStatistics.transitionProposal] so the caller can act on them.
 */
class ConversationManager(
    private val agentFactory: (systemPrompt: String, toolRegistry: ToolRegistry) -> AIAgent<String, String>,
    val strategy: ContextStrategy,
    val profileManager: ProfileManager = ProfileManager(),
    val invariantStorage: InvariantStorage = InvariantStorage()
) {
    private var baseSystemPrompt: String =
        "You are a helpful assistant. Answer user questions concisely."
    private var taskFsm: TaskFSM? = null
    private var autoMode: Boolean = false
    private var mcpToolRegistry: ToolRegistry = ToolRegistry.EMPTY
    private var mcpConnectionInfo: McpConnectionManager.ConnectionInfo? = null

    fun setMcpToolRegistry(registry: ToolRegistry, connectionInfo: McpConnectionManager.ConnectionInfo) {
        // Merge: append new server's tools to existing registry
        mcpToolRegistry += registry
        mcpConnectionInfo = connectionInfo
    }

    fun clearMcpToolRegistry() {
        mcpToolRegistry = ToolRegistry.EMPTY
        mcpConnectionInfo = null
    }

    fun setTaskFsm(fsm: TaskFSM?) {
        taskFsm = fsm
    }

    fun setAutoMode(enabled: Boolean) {
        autoMode = enabled
    }

    fun isAutoMode(): Boolean = autoMode

    suspend fun sendMessage(userMessage: String): ChatResult<RequestStatistics> {
        return try {
            val enrichedSystemPrompt = buildSystemPrompt()
            val agent = agentFactory(enrichedSystemPrompt, mcpToolRegistry)

            val inputTokens = TokenCounter.estimate(userMessage)
            val statsBeforeTurn = strategy.estimateTokenStats()

            val rawResponse = agent.run(userMessage)

            // In auto mode: parse and strip the transition marker before storing history
            val proposal = if (autoMode) parseTransitionProposal(rawResponse) else null
            val response = if (proposal != null) stripTransitionMarker(rawResponse) else rawResponse

            val responseTokens = TokenCounter.estimate(response)

            // Post-turn: add to history (may trigger async side-effects like fact extraction)
            strategy.addMessage(userMessage, response)

            // Persist sliding-window history for session restore
            if (strategy is llmchat.agent.context.SlidingWindowStrategy) {
                ConversationStorage.save(strategy.getMessages(), emptyList())
            }

            Result.success(
                RequestStatistics(
                    response = response,
                    inputTokens = inputTokens,
                    windowTokens = statsBeforeTurn.primary,
                    summaryTokens = statsBeforeTurn.secondary,
                    responseTokens = responseTokens,
                    longTermTokens = statsBeforeTurn.tertiary,
                    transitionProposal = proposal
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTransitionProposal(response: String): TaskTransitionProposal? {
        val match = TRANSITION_REGEX.find(response) ?: return null
        val stageName = match.groupValues[1].uppercase()
        val step = match.groupValues[2].trim()
        val reason = match.groupValues[3].trim()
        val stage = llmchat.agent.task.TaskStage.entries.find { it.name == stageName } ?: return null
        return TaskTransitionProposal(targetStage = stage, step = step, reason = reason)
    }

    private fun stripTransitionMarker(response: String): String =
        TRANSITION_REGEX.replace(response, "").trimEnd()

    private fun buildSystemPrompt(): String {
        val invariantBlock = invariantStorage.buildPromptBlock()
        val profileBlock = profileManager.buildPromptBlock()
        val contextBlock = strategy.buildContextBlock()
        val taskBlock = taskFsm?.buildResumptionContext(autoMode) ?: ""
        val mcpBlock = mcpConnectionInfo?.buildPromptBlock() ?: ""
        return buildString {
            append(baseSystemPrompt)
            // Invariants first: constraints must appear before any other context
            // so the model evaluates them with maximum attention weight.
            if (invariantBlock.isNotEmpty()) {
                append("\n\n")
                append(invariantBlock)
            }
            if (profileBlock.isNotEmpty()) {
                append("\n\n")
                append(profileBlock)
            }
            if (contextBlock.isNotEmpty()) {
                append("\n\n")
                append(contextBlock)
            }
            if (taskBlock.isNotEmpty()) {
                append("\n\n")
                append(taskBlock)
            }
            if (mcpBlock.isNotEmpty()) {
                append("\n\n")
                append(mcpBlock)
            }
        }
    }

    fun setBaseSystemPrompt(prompt: String) {
        baseSystemPrompt = prompt
    }

    fun clearHistory() {
        strategy.clearHistory()
        ConversationStorage.clear()
    }

    fun loadInitialMessages(messages: List<Pair<String, String>>) {
        strategy.loadMessages(messages)
    }

    fun displayHistory() {
        strategy.displayHistory()
    }

    companion object {
        private val TRANSITION_REGEX = Regex(
            """\[TASK_TRANSITION\]\s*\nto:\s*(\S+)\s*\nstep:\s*(.*?)\s*\nreason:\s*(.*?)\s*\[/TASK_TRANSITION\]""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
    }
}
