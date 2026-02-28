package llmchat.ui

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors.blue
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.terminal.Terminal
import llmchat.agent.context.Branch
import llmchat.agent.context.Checkpoint
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
        totalTokens: Int
    ) {
        val parts = mutableListOf(
            "in: ~$inputTokens",
            "ctx: ~$windowTokens"
        )
        if (summaryTokens > 0) parts.add("facts: ~$summaryTokens")
        parts.add("out: ~$responseTokens")
        parts.add("total: ~$totalTokens")
        terminal.println(dim("   [${parts.joinToString(" | ")}]"))
        terminal.println()
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

    // ── Legacy stubs (kept for backward compatibility during transition) ────────

    /** @deprecated Use ThinkingSpinner instead */
    fun printThinkingIndicator() {}

    /** @deprecated Use ThinkingSpinner instead */
    fun clearThinkingIndicator() {}
}
