package llmchat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import kotlinx.coroutines.runBlocking
import llmchat.agent.ConversationManager
import llmchat.agent.ConversationStorage
import llmchat.agent.context.BranchingStrategy
import llmchat.agent.context.ContextStrategy
import llmchat.agent.context.SlidingWindowStrategy
import llmchat.agent.context.StickyFactsStrategy
import llmchat.cli.CliParser
import llmchat.cli.Command
import llmchat.cli.StrategyType
import llmchat.ui.CliOutput
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val config = try {
            CliParser.parse(args)
        } catch (e: IllegalArgumentException) {
            CliOutput.printError(e.message ?: "Invalid arguments")
            println()
            CliParser.printHelp()
            exitProcess(1)
        }

        if (config.showHelp) {
            CliParser.printHelp()
            exitProcess(0)
        }

        val apiKey = System.getenv("OPENROUTER_API_KEY")
        if (apiKey.isNullOrBlank()) {
            CliOutput.printError("OPENROUTER_API_KEY environment variable is not set.")
            println("Please set your OpenRouter API key:")
            println("  export OPENROUTER_API_KEY='your-api-key-here'")
            exitProcess(1)
        }

        runBlocking {
            startInteractiveCli(apiKey, config)
        }
    } catch (e: Exception) {
        println("Fatal error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

suspend fun startInteractiveCli(apiKey: String, config: llmchat.cli.CliConfig) {
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\nGoodbye!")
    })

    val promptExecutor = simpleOpenRouterExecutor(apiKey)

    val agentFactory: (String) -> AIAgent<String, String> = { systemPrompt ->
        AIAgent(
            promptExecutor = promptExecutor,
            systemPrompt = systemPrompt,
            llmModel = config.model.openRouterModel,
            temperature = config.temperature
        )
    }

    val strategy: ContextStrategy = when (config.strategyType) {
        StrategyType.SLIDING_WINDOW ->
            SlidingWindowStrategy(config.contextWindow.windowSize)

        StrategyType.STICKY_FACTS ->
            StickyFactsStrategy(
                windowSize = config.contextWindow.windowSize,
                agentFactory = agentFactory
            )

        StrategyType.BRANCHING ->
            BranchingStrategy(config.contextWindow.windowSize)
    }

    val conversationManager = ConversationManager(
        agentFactory = agentFactory,
        strategy = strategy
    )
    conversationManager.setBaseSystemPrompt(config.systemPrompt)

    // Session restore — only applicable for SlidingWindow (linear history)
    if (strategy is SlidingWindowStrategy && ConversationStorage.hasHistory()) {
        val count = ConversationStorage.size()
        print("Found previous conversation ($count messages). Resume? [y/N]: ")
        val answer = readlnOrNull()?.trim()?.lowercase()
        if (answer == "y" || answer == "yes") {
            conversationManager.loadInitialMessages(ConversationStorage.loadRecentTurns())
            CliOutput.printInfo("Loaded $count messages from previous session.")
        } else {
            ConversationStorage.clear()
        }
    }

    CliOutput.printWelcome(config)

    while (true) {
        val prompt = when (strategy) {
            is BranchingStrategy -> "\n[${strategy.currentBranchName()}] You: "
            else -> "\nYou: "
        }
        print(prompt)

        val input = readMultilineInput() ?: break
        if (input.isEmpty()) continue

        when (val command = Command.parse(input)) {
            is Command.Exit -> {
                CliOutput.printGoodbye()
                break
            }

            is Command.Help -> CliOutput.printInteractiveHelp(config.strategyType)

            is Command.Clear -> {
                conversationManager.clearHistory()
                CliOutput.printInfo("Conversation history cleared.")
            }

            is Command.History -> conversationManager.displayHistory()

            is Command.StrategyInfo -> CliOutput.printStrategyInfo(config.strategyType)

            // ── Branch commands ────────────────────────────────────────────

            is Command.BranchList -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    CliOutput.printError("Branch commands require --strategy branching")
                } else {
                    CliOutput.printBranchList(bs.listBranches(), bs.currentBranchName())
                }
            }

            is Command.BranchNew -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    CliOutput.printError("Branch commands require --strategy branching")
                } else {
                    try {
                        val branch = bs.createBranch(command.name, command.fromCheckpoint)
                        CliOutput.printInfo(
                            "Created and switched to branch '${branch.name}'" +
                                    (if (command.fromCheckpoint != null) " (forked from checkpoint '${command.fromCheckpoint}')" else " (forked from current state)")
                        )
                    } catch (e: IllegalArgumentException) {
                        CliOutput.printError(e.message ?: "Failed to create branch")
                    }
                }
            }

            is Command.BranchSwitch -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    CliOutput.printError("Branch commands require --strategy branching")
                } else {
                    try {
                        val branch = bs.switchBranch(command.name)
                        CliOutput.printInfo("Switched to branch '${branch.name}' (${branch.messages.size} exchanges)")
                    } catch (e: IllegalArgumentException) {
                        CliOutput.printError(e.message ?: "Failed to switch branch")
                    }
                }
            }

            // ── Checkpoint commands ────────────────────────────────────────

            is Command.CheckpointList -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    CliOutput.printError("Checkpoint commands require --strategy branching")
                } else {
                    CliOutput.printCheckpointList(bs.listCheckpoints())
                }
            }

            is Command.CheckpointSave -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    CliOutput.printError("Checkpoint commands require --strategy branching")
                } else {
                    val cp = bs.saveCheckpoint(command.name)
                    CliOutput.printInfo(
                        "Checkpoint '${cp.name}' saved at message ${cp.messageCount} of branch '${bs.currentBranchName()}'."
                    )
                }
            }

            // ── Facts commands ─────────────────────────────────────────────

            is Command.FactsList -> {
                val fs = strategy as? StickyFactsStrategy
                if (fs == null) {
                    CliOutput.printError("Facts commands require --strategy sticky-facts")
                } else {
                    CliOutput.printFacts(fs.getFacts())
                }
            }

            is Command.FactsSet -> {
                val fs = strategy as? StickyFactsStrategy
                if (fs == null) {
                    CliOutput.printError("Facts commands require --strategy sticky-facts")
                } else {
                    fs.setFact(command.key, command.value)
                    CliOutput.printInfo("Fact set: ${command.key} = ${command.value}")
                }
            }

            is Command.FactsDelete -> {
                val fs = strategy as? StickyFactsStrategy
                if (fs == null) {
                    CliOutput.printError("Facts commands require --strategy sticky-facts")
                } else {
                    fs.deleteFact(command.key)
                    CliOutput.printInfo("Fact '${command.key}' removed.")
                }
            }

            is Command.Unknown -> {
                CliOutput.printError("Unknown command: ${command.input}")
                println("Type /help for available commands.")
            }

            is Command.Message -> handleMessage(conversationManager, command.content)
        }
    }
}

suspend fun handleMessage(conversationManager: ConversationManager, message: String) {
    try {
        CliOutput.printThinkingIndicator()

        val result = conversationManager.sendMessage(message)

        result.fold(
            onSuccess = { stats ->
                CliOutput.clearThinkingIndicator()
                println(stats.response)
                CliOutput.printTokenStats(
                    inputTokens = stats.inputTokens,
                    windowTokens = stats.windowTokens,
                    summaryTokens = stats.summaryTokens,
                    responseTokens = stats.responseTokens,
                    totalTokens = stats.totalTokens
                )
            },
            onFailure = { error ->
                println()
                CliOutput.printError("Error communicating with LLM: ${error.message}")
                println("Please try again or type /exit to quit.")
            }
        )
    } catch (e: Exception) {
        println()
        CliOutput.printError("Unexpected error: ${e.message}")
        println("Please try again or type /exit to quit.")
    }
}

fun readMultilineInput(): String? {
    val lines = mutableListOf<String>()

    while (true) {
        val line = readlnOrNull() ?: return if (lines.isEmpty()) null else lines.joinToString("\n")

        if (line.trim().isEmpty() && lines.isNotEmpty()) {
            break
        }

        if (line.trim().isEmpty() && lines.isEmpty()) {
            continue
        }

        lines.add(line)
    }

    return lines.joinToString("\n").trim()
}
