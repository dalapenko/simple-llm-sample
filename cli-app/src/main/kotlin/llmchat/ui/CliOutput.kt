package llmchat.ui

import ai.koog.agents.core.tools.Tool
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors.blue
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.magenta
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.terminal.Terminal
import llmchat.agent.context.Branch
import llmchat.agent.context.Checkpoint
import llmchat.agent.invariant.Invariant
import llmchat.agent.invariant.InvariantCategory
import llmchat.agent.mcp.McpConnectionManager
import llmchat.agent.memory.MemoryItem
import llmchat.agent.memory.MemoryLayer
import llmchat.agent.profile.ProfileManager
import llmchat.agent.task.TaskStage
import llmchat.agent.task.TaskState
import llmchat.cli.CliConfig
import llmchat.cli.StrategyType

class CliOutput(private val terminal: Terminal) {

    fun printWelcome(config: CliConfig) {
        terminal.println()
        terminal.println(
            bold(" LLM Chat") + dim(" | ") +
                    cyan(config.model.displayName) + dim(" | ") +
                    config.strategyType.cliName + dim(" | ") +
                    dim("/help for commands")
        )
        terminal.println(dim("─".repeat(72)))
        terminal.println()
    }

    fun printInteractiveHelp(strategyType: StrategyType) {
        terminal.println()
        terminal.println(bold("Commands:"))
        listOf(
            "/help" to "Show this help",
            "/clear" to "Clear conversation history",
            "/history" to "Show conversation history",
            "/strategy" to "Show active context strategy",
            "/exit, /quit" to "Exit the application"
        ).forEach { (cmd, desc) ->
            terminal.println("  ${cyan(cmd.padEnd(22))} $desc")
        }

        terminal.println()
        terminal.println(bold("Invariants:"))
        listOf(
            "/invariant list" to "List all project invariants",
            "/invariant add <desc>" to "Add invariant (category: general)",
            "/invariant add --category <cat> <desc>" to "Add invariant with category (stack|architecture|business-rule|general)",
            "/invariant remove <id>" to "Remove invariant by ID",
            "/invariant clear" to "Remove all invariants"
        ).forEach { (cmd, desc) ->
            terminal.println("  ${cyan(cmd.padEnd(42))} $desc")
        }

        terminal.println()
        terminal.println(bold("Profile:"))
        listOf(
            "/profile" to "Show profile status",
            "/profile path" to "Show profile file path",
            "/profile reload" to "Reload profile.md from disk"
        ).forEach { (cmd, desc) ->
            terminal.println("  ${cyan(cmd.padEnd(22))} $desc")
        }

        val extra: List<Pair<String, String>> = when (strategyType) {
            StrategyType.STICKY_FACTS -> listOf(
                "/facts" to "Show current facts map",
                "/facts set K V" to "Set fact K to value V",
                "/facts delete K" to "Remove fact K"
            )

            StrategyType.BRANCHING -> listOf(
                "/branch list" to "List all branches",
                "/branch new <name>" to "Fork current state into a new branch",
                "/branch new <name> from <cp>" to "Fork from a saved checkpoint",
                "/branch switch <name>" to "Switch active branch",
                "/checkpoint list" to "List saved checkpoints",
                "/checkpoint save <name>" to "Save checkpoint at current position"
            )

            StrategyType.LAYERED -> listOf(
                "/memory list [layer]" to "List items (layer: short-term | work | long-term)",
                "/memory add <layer> <data>" to "Add data to a memory layer",
                "/memory delete <layer> <id>" to "Delete an item by id",
                "/memory clear <layer>" to "Clear all items from a layer"
            )

            else -> emptyList()
        }

        if (extra.isNotEmpty()) {
            terminal.println()
            terminal.println(bold("Strategy-specific (${strategyType.cliName}):"))
            extra.forEach { (cmd, desc) ->
                terminal.println("  ${cyan(cmd.padEnd(38))} $desc")
            }
        }

        terminal.println()
        terminal.println(bold("Task tracking:"))
        listOf(
            "/task start <desc>" to "Start tracking a new task",
            "/task status" to "Show current task state",
            "/task advance <stage>" to "Advance stage (planning|execution|validation|done|error)",
            "/task step <desc>" to "Update current step description",
            "/task pause" to "Save task state (resume on next startup)",
            "/task done" to "Mark task done and clear state",
            "/task cancel" to "Cancel and discard task state"
        ).forEach { (cmd, desc) ->
            terminal.println("  ${cyan(cmd.padEnd(30))} $desc")
        }

        terminal.println()
        terminal.println(bold("MCP (Model Context Protocol):"))
        listOf(
            "/mcp connect <cmd> [args]" to "Connect to MCP server via stdio",
            "/mcp tools" to "List tools from connected server",
            "/mcp status" to "Show MCP connection status",
            "/mcp disconnect" to "Disconnect from MCP server"
        ).forEach { (cmd, desc) ->
            terminal.println("  ${cyan(cmd.padEnd(30))} $desc")
        }

        terminal.println()
        terminal.println(dim("Tip: press \\ at end of line for multi-line input. Tab completes commands."))
        terminal.println()
    }

    fun printStrategyInfo(strategyType: StrategyType) {
        terminal.println()
        terminal.println(dim("Active strategy: ") + cyan(strategyType.displayName) + dim(" (${strategyType.cliName})"))
        terminal.println()
    }

    fun printAssistantResponse(rawResponse: String) {
        val sanitized = AnsiSanitizer.strip(rawResponse)
        terminal.println()
        terminal.println(blue(bold(" AI")))
        terminal.println(Markdown(sanitized))
    }

    fun printTokenStats(
        inputTokens: Int,
        windowTokens: Int,
        summaryTokens: Int,
        responseTokens: Int,
        totalTokens: Int,
        longTermTokens: Int = 0
    ) {
        val parts = mutableListOf(
            "in: ~$inputTokens",
            "ctx: ~$windowTokens"
        )
        if (summaryTokens > 0) parts.add("work: ~$summaryTokens")
        if (longTermTokens > 0) parts.add("long: ~$longTermTokens")
        parts.add("out: ~$responseTokens")
        parts.add("total: ~$totalTokens")
        terminal.println(dim("   [${parts.joinToString(" | ")}]"))
        terminal.println()
    }

    fun printMemoryList(layer: MemoryLayer?, itemsByLayer: Map<MemoryLayer, List<MemoryItem>>) {
        terminal.println()
        val layers = if (layer != null) listOf(layer) else MemoryLayer.entries
        layers.forEach { l ->
            val items = itemsByLayer[l] ?: emptyList()
            terminal.println(bold(" ${l.displayName}"))
            terminal.println(dim("─".repeat(50)))
            if (items.isEmpty()) {
                terminal.println(dim("  (empty)"))
            } else {
                items.forEach { item ->
                    terminal.println("  ${dim("[${item.id}]")} ${item.content}")
                }
            }
            terminal.println(dim("─".repeat(50)))
            terminal.println()
        }
    }

    fun printBranchList(branches: List<Branch>, currentBranchName: String) {
        terminal.println()
        terminal.println(bold(" Branches"))
        terminal.println(dim("─".repeat(40)))
        if (branches.isEmpty()) {
            terminal.println(dim("  (none)"))
        } else {
            branches.forEach { branch ->
                val marker = if (branch.name == currentBranchName) "  " + green("◀ current") else ""
                terminal.println("  ${cyan(branch.name.padEnd(20))} ${dim("${branch.messages.size} exchanges")}$marker")
            }
        }
        terminal.println(dim("─".repeat(40)))
        terminal.println()
    }

    fun printCheckpointList(checkpoints: List<Checkpoint>) {
        terminal.println()
        terminal.println(bold(" Checkpoints"))
        terminal.println(dim("─".repeat(40)))
        if (checkpoints.isEmpty()) {
            terminal.println(dim("  (none saved yet — use /checkpoint save <name>)"))
        } else {
            checkpoints.forEach { cp ->
                terminal.println(
                    "  ${cyan(cp.name.padEnd(20))} ${dim("branch=${cp.branchId}, msg ${cp.messageCount}")}"
                )
            }
        }
        terminal.println(dim("─".repeat(40)))
        terminal.println()
    }

    fun printFacts(facts: Map<String, String>) {
        terminal.println()
        terminal.println(bold(" Facts"))
        terminal.println(dim("─".repeat(40)))
        if (facts.isEmpty()) {
            terminal.println(dim("  (no facts extracted yet — start chatting to populate)"))
        } else {
            facts.forEach { (k, v) ->
                terminal.println("  ${cyan(k)} ${dim("=")} $v")
            }
        }
        terminal.println(dim("─".repeat(40)))
        terminal.println()
    }

    fun printInvariants(invariants: List<Invariant>) {
        terminal.println()
        terminal.println(bold(" Project Invariants"))
        terminal.println(dim("─".repeat(60)))
        if (invariants.isEmpty()) {
            terminal.println(dim("  (none defined — use /invariant add [--category <cat>] <desc>)"))
            terminal.println(dim("  Categories: stack | architecture | business-rule | general"))
        } else {
            val byCategory = invariants.groupBy { it.category }
            InvariantCategory.entries.forEach { cat ->
                val items = byCategory[cat] ?: return@forEach
                terminal.println()
                terminal.println("  ${bold(cat.displayName)}")
                items.forEach { inv ->
                    terminal.println("  ${dim("[${inv.id}]")} ${yellow(inv.description)}")
                }
            }
        }
        terminal.println(dim("─".repeat(60)))
        terminal.println()
    }

    fun printProfileStatus(profileManager: ProfileManager) {
        terminal.println()
        terminal.println(bold(" User Profile"))
        terminal.println(dim("─".repeat(50)))
        terminal.println("  ${dim("File:")} ${profileManager.filePath()}")
        val profile = profileManager.getProfile()
        if (profile == null) {
            terminal.println("  ${dim("Status:")} ${yellow("not active")} ${dim("(file missing or empty)")}")
        } else {
            val tokens = profileManager.estimateTokens()
            terminal.println("  ${dim("Status:")} ${green("active")} ${dim("(~$tokens tokens)")}")
            terminal.println()
            terminal.println(dim("  Preview (first 200 chars):"))
            val preview = profile.content.take(200).replace("\n", " ↵ ")
            terminal.println(dim("  $preview${if (profile.content.length > 200) "…" else ""}"))
        }
        terminal.println(dim("─".repeat(50)))
        terminal.println()
    }

    fun printTaskStatus(state: TaskState) {
        val stageColor = stageColor(state.stage)
        terminal.println()
        terminal.println(bold(" Task Status"))
        terminal.println(dim("─".repeat(50)))
        terminal.println("  ${dim("ID:")}          ${dim(state.id.take(8))}…")
        terminal.println("  ${dim("Task:")}        ${state.taskDescription}")
        terminal.println("  ${dim("Stage:")}       ${stageColor(state.stage.displayName)}")
        terminal.println("  ${dim("Step:")}        ${state.currentStep}")
        terminal.println("  ${dim("Next action:")} ${state.expectedAction.displayName}")
        if (state.history.isNotEmpty()) {
            terminal.println("  ${dim("Transitions:")} ${state.history.size}")
        }
        terminal.println(dim("─".repeat(50)))
        terminal.println()
    }

    fun printTaskResume(state: TaskState) {
        val stageColor = stageColor(state.stage)
        terminal.println()
        terminal.println(yellow(bold(" ⟳ Resuming Task")))
        terminal.println(dim("─".repeat(50)))
        terminal.println("  ${dim("Task:")}  ${state.taskDescription}")
        terminal.println("  ${dim("Stage:")} ${stageColor(state.stage.displayName)}")
        terminal.println("  ${dim("Step:")}  ${state.currentStep}")
        terminal.println(dim("─".repeat(50)))
        terminal.println()
    }

    fun printTransitionProposal(proposal: llmchat.agent.task.TaskTransitionProposal, requiresApproval: Boolean) {
        val stageColor = stageColor(proposal.targetStage)
        terminal.println()
        terminal.println(magenta(bold(" ⟳ Task Transition Proposed")))
        terminal.println(dim("─".repeat(50)))
        terminal.println("  ${dim("Target:")} ${stageColor(proposal.targetStage.displayName)}")
        terminal.println("  ${dim("Step:")}   ${proposal.step}")
        terminal.println("  ${dim("Reason:")} ${proposal.reason}")
        if (requiresApproval) {
            terminal.println(dim("─".repeat(50)))
            terminal.println("  ${yellow("Approval required.")} Type ${bold("y")} to proceed or ${bold("n")} to reject.")
        }
        terminal.println(dim("─".repeat(50)))
    }

    fun printAutoMode(enabled: Boolean) {
        val label = if (enabled) green(bold("ON")) else dim("OFF")
        terminal.println(dim("  Autonomous mode: ") + label)
    }

    fun printTaskTransition(from: TaskStage, to: TaskStage) {
        val fromColor = stageColor(from)
        val toColor = stageColor(to)
        terminal.println(dim("  Task: ") + fromColor(from.displayName) + dim(" → ") + toColor(to.displayName))
    }

    private fun stageColor(stage: TaskStage): (String) -> String = when (stage) {
        TaskStage.PLANNING -> { s -> yellow(s) }
        TaskStage.PLAN_APPROVED -> { s -> magenta(s) }
        TaskStage.EXECUTION -> { s -> cyan(s) }
        TaskStage.VALIDATION -> { s -> blue(s) }
        TaskStage.DONE -> { s -> green(s) }
        TaskStage.ERROR -> { s -> red(s) }
    }

    fun printError(message: String) {
        terminal.println(red(bold(" ERROR ")) + red(message))
    }

    fun printInfo(message: String) {
        terminal.println(dim("  $message"))
    }

    fun printGoodbye() {
        terminal.println()
        terminal.println(dim("Goodbye!"))
    }

    /**
     * Build the styled ANSI prompt string passed to JLine's readLine().
     * JLine renders raw ANSI codes so Mordant color functions work directly here.
     */
    fun buildPrompt(branchName: String? = null): String {
        val branch = if (branchName != null) {
            "${dim("[") + cyan(branchName) + dim("]")} "
        } else ""
        return "\n ${branch}${cyan(bold("You"))} ${dim(">")} "
    }

    // ── MCP commands ───────────────────────────────────────────────────────────

    /**
     * Generic banner for a push notification received from any MCP server via stderr.
     * Returns a string for [org.jline.reader.LineReader.printAbove] — no scheduler-specific text.
     */
    fun buildMcpNotificationBanner(title: String, description: String): String {
        val sep = yellow("─".repeat(60))
        val titleStr = bold(title)
        val descPart = if (description.isNotBlank()) dim(" — $description") else ""
        return "$sep\n ${yellow("●")} $titleStr$descPart\n$sep"
    }

    fun printMcpConnected(info: McpConnectionManager.ConnectionInfo, toolCount: Int, totalTools: Int) {
        terminal.println()
        terminal.println(green(bold(" MCP Connected")))
        terminal.println(dim("─".repeat(60)))
        terminal.println("  ${dim("Server:")} ${AnsiSanitizer.strip(info.commandLine)}")
        terminal.println("  ${dim("Tools:")}  ${green("$toolCount from this server")}${if (totalTools != toolCount) dim(" ($totalTools total across all servers)") else ""}")
        terminal.println(dim("─".repeat(60)))
        terminal.println()
    }

    fun printMcpTools(tools: List<Tool<*, *>>) {
        terminal.println()
        terminal.println(bold(" MCP Tools"))
        terminal.println(dim("─".repeat(60)))
        if (tools.isEmpty()) {
            terminal.println(dim("  (server exposes no tools)"))
        } else {
            tools.forEachIndexed { index, tool ->
                val connector = if (index == tools.lastIndex) "└─" else "├─"
                val name = AnsiSanitizer.strip(tool.name)
                val desc = AnsiSanitizer.strip(tool.descriptor.description).let {
                    if (it.length > 70) it.take(70) + "…" else it
                }
                terminal.println("  $connector ${cyan(name)}")
                terminal.println("     ${dim(desc)}")

                val required = tool.descriptor.requiredParameters
                val optional = tool.descriptor.optionalParameters
                val allParams = required.map { it to true } + optional.map { it to false }
                allParams.forEachIndexed { pIdx, (param, isRequired) ->
                    val isLastParam = pIdx == allParams.lastIndex
                    val branch = if (isLastParam) "   └─" else "   ├─"
                    val reqLabel = if (isRequired) "" else dim(" (optional)")
                    terminal.println(
                        "  $branch ${param.name.padEnd(16)} ${dim(param.type.name.lowercase().padEnd(8))} " +
                                "${dim(param.description)}$reqLabel"
                    )
                }
            }
        }
        terminal.println(dim("─".repeat(60)))
        terminal.println()
    }

    fun printMcpStatus(connections: List<McpConnectionManager.ConnectionInfo>) {
        terminal.println()
        terminal.println(bold(" MCP Status"))
        terminal.println(dim("─".repeat(60)))
        if (connections.isEmpty()) {
            terminal.println("  ${dim("Status:")}  ${yellow("not connected")}")
            terminal.println(dim("  Use: /mcp connect <command> [args...]"))
        } else {
            terminal.println("  ${dim("Status:")}  ${green("${connections.size} server(s) connected")}")
            connections.forEachIndexed { i, info ->
                val connector = if (i == connections.lastIndex) "└─" else "├─"
                terminal.println("  $connector ${AnsiSanitizer.strip(info.commandLine)}")
            }
        }
        terminal.println(dim("─".repeat(60)))
        terminal.println()
    }

    fun printMcpDisconnected() {
        terminal.println(dim("  MCP server disconnected."))
    }

    /**
     * Prints an MCP tool call line, clearing the current spinner line first.
     * Uses raw stdout so it works safely while the spinner animation is active.
     */
    fun printMcpToolCall(toolName: String, argsPreview: String) {
        // \r\u001B[K clears the spinner line; \n pushes spinner down to a fresh line
        print("\r\u001B[K  ${cyan("→ [MCP]")} ${bold(toolName)}${dim("($argsPreview)")}\n")
        System.out.flush()
    }

    fun printMcpToolResult(toolName: String, resultPreview: String) {
        print("\r\u001B[K  ${dim("✓ [MCP]")} ${dim(toolName)} ${dim("→")} ${dim(resultPreview)}\n")
        System.out.flush()
    }

    // ── Legacy stubs (kept for backward compatibility during transition) ────────

    /** @deprecated Use ThinkingSpinner instead */
    fun printThinkingIndicator() {}

    /** @deprecated Use ThinkingSpinner instead */
    fun clearThinkingIndicator() {}
}
