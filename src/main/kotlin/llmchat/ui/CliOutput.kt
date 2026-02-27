package llmchat.ui

import llmchat.agent.context.Branch
import llmchat.agent.context.Checkpoint
import llmchat.cli.CliConfig
import llmchat.cli.StrategyType

object CliOutput {

    fun printWelcome(config: CliConfig) {
        println(
            """

        ╔═══════════════════════════════════════╗
        ║         LLM Chat CLI                  ║
        ║  Powered by Koog & OpenRouter         ║
        ╚═══════════════════════════════════════╝

        Model: ${config.model.displayName}
        Temperature: ${config.temperature}
        Strategy: ${config.strategyType.displayName}

        Type your message and press Enter twice to send.
        Commands: /help, /clear, /history, /strategy, /exit

        """.trimIndent()
        )
    }

    fun printInteractiveHelp(strategyType: StrategyType) {
        val strategyCommands = when (strategyType) {
            StrategyType.STICKY_FACTS -> """
          /facts              Show the current facts map
          /facts set K V      Manually set fact K to value V
          /facts delete K     Remove fact K"""

            StrategyType.BRANCHING -> """
          /branch list                     List all branches
          /branch new <name>               Fork current state into a new branch
          /branch new <name> from <cp>     Fork from a saved checkpoint
          /branch switch <name>            Switch active branch
          /checkpoint list                 List saved checkpoints
          /checkpoint save <name>          Save checkpoint at current position"""

            else -> ""
        }

        println(
            """

        Available Commands:
          /help               Show this help message
          /clear              Clear conversation history
          /history            Show conversation history
          /strategy           Show current context strategy
          /exit, /quit        Exit the application
        ${if (strategyCommands.isNotEmpty()) "\n        Strategy-specific (${strategyType.cliName}):\n$strategyCommands" else ""}

        How to use:
          - Type your message (can be multiple lines)
          - Press Enter twice (empty line) to send
        """.trimIndent()
        )
    }

    fun printStrategyInfo(strategyType: StrategyType) {
        println("\nActive strategy: ${strategyType.displayName} (${strategyType.cliName})")
    }

    fun printTokenStats(
        inputTokens: Int,
        windowTokens: Int,
        summaryTokens: Int,
        responseTokens: Int,
        totalTokens: Int
    ) {
        val secondaryLabel = if (summaryTokens > 0) " | facts: ~$summaryTokens" else ""
        println("\n[Tokens] request: ~$inputTokens | window: ~$windowTokens$secondaryLabel | response: ~$responseTokens | total: ~$totalTokens")
    }

    // ── Branch display ─────────────────────────────────────────────────────────

    fun printBranchList(branches: List<Branch>, currentBranchName: String) {
        println("\n=== Branches ===")
        if (branches.isEmpty()) {
            println("  (none)")
        } else {
            branches.forEach { branch ->
                val marker = if (branch.name == currentBranchName) " ◀ current" else ""
                println("  ${branch.name.padEnd(20)} ${branch.messages.size} exchanges$marker")
            }
        }
        println("================\n")
    }

    fun printCheckpointList(checkpoints: List<Checkpoint>) {
        println("\n=== Checkpoints ===")
        if (checkpoints.isEmpty()) {
            println("  (none saved yet — use /checkpoint save <name>)")
        } else {
            checkpoints.forEach { cp ->
                println("  ${cp.name.padEnd(20)} branch=${cp.branchId}, at message ${cp.messageCount}")
            }
        }
        println("===================\n")
    }

    // ── Facts display ──────────────────────────────────────────────────────────

    fun printFacts(facts: Map<String, String>) {
        println("\n=== Facts ===")
        if (facts.isEmpty()) {
            println("  (no facts extracted yet — start chatting to populate)")
        } else {
            facts.forEach { (k, v) -> println("  $k = $v") }
        }
        println("=============\n")
    }

    // ── Standard messages ──────────────────────────────────────────────────────

    fun printThinkingIndicator() {
        print("Assistant: Thinking...")
    }

    fun clearThinkingIndicator() {
        print("\r")
        print("Assistant: ")
    }

    fun printError(message: String) {
        println("Error: $message")
    }

    fun printInfo(message: String) {
        println(message)
    }

    fun printGoodbye() {
        println("Goodbye!")
    }
}
