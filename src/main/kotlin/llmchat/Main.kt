package llmchat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
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
import llmchat.ui.ChatInputReader
import llmchat.ui.CliOutput
import llmchat.ui.ThinkingSpinner
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val terminal = Terminal()
    val output = CliOutput(terminal)

    try {
        val config = try {
            CliParser.parse(args)
        } catch (e: IllegalArgumentException) {
            output.printError(e.message ?: "Invalid arguments")
            terminal.println()
            CliParser.printHelp()
            exitProcess(1)
        }

        if (config.showHelp) {
            CliParser.printHelp()
            exitProcess(0)
        }

        val apiKey = System.getenv("OPENROUTER_API_KEY")
        if (apiKey.isNullOrBlank()) {
            output.printError("OPENROUTER_API_KEY environment variable is not set.")
            terminal.println("Please set your OpenRouter API key:")
            terminal.println("  export OPENROUTER_API_KEY='your-api-key-here'")
            exitProcess(1)
        }

        runBlocking {
            startInteractiveCli(apiKey, config, output, terminal, this)
        }
    } catch (e: Exception) {
        output.printError("Fatal error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

suspend fun startInteractiveCli(
    apiKey: String,
    config: llmchat.cli.CliConfig,
    output: CliOutput,
    terminal: Terminal,
    scope: CoroutineScope
) {
    val inputReader = ChatInputReader(config.strategyType)
    val spinner = ThinkingSpinner(terminal)

    Runtime.getRuntime().addShutdownHook(Thread {
        inputReader.close()
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

    // Session restore — only for SlidingWindow (linear history)
    if (strategy is SlidingWindowStrategy && ConversationStorage.hasHistory()) {
        val count = ConversationStorage.size()
        val answer = inputReader.readInput(
            terminal.theme.style("prompt")(" Found previous conversation ($count messages). Resume? [y/N]: ")
                .ifEmpty { " Found previous conversation ($count messages). Resume? [y/N]: " }
        )?.trim()?.lowercase()
        if (answer == "y" || answer == "yes") {
            conversationManager.loadInitialMessages(ConversationStorage.loadRecentTurns())
            output.printInfo("Loaded $count messages from previous session.")
        } else {
            ConversationStorage.clear()
        }
    }

    output.printWelcome(config)

    while (true) {
        val branchName = (strategy as? BranchingStrategy)?.currentBranchName()
        val prompt = output.buildPrompt(branchName)

        val input = inputReader.readInput(prompt) ?: break
        if (input.isEmpty()) continue

        when (val command = Command.parse(input)) {
            is Command.Exit -> {
                output.printGoodbye()
                break
            }

            is Command.Help -> output.printInteractiveHelp(config.strategyType)

            is Command.Clear -> {
                conversationManager.clearHistory()
                output.printInfo("Conversation history cleared.")
            }

            is Command.History -> conversationManager.displayHistory()

            is Command.StrategyInfo -> output.printStrategyInfo(config.strategyType)

            // ── Branch commands ────────────────────────────────────────────

            is Command.BranchList -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    output.printError("Branch commands require --strategy branching")
                } else {
                    output.printBranchList(bs.listBranches(), bs.currentBranchName())
                }
            }

            is Command.BranchNew -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    output.printError("Branch commands require --strategy branching")
                } else {
                    try {
                        val branch = bs.createBranch(command.name, command.fromCheckpoint)
                        output.printInfo(
                            "Created and switched to branch '${branch.name}'" +
                                    (if (command.fromCheckpoint != null) " (forked from checkpoint '${command.fromCheckpoint}')" else " (forked from current state)")
                        )
                    } catch (e: IllegalArgumentException) {
                        output.printError(e.message ?: "Failed to create branch")
                    }
                }
            }

            is Command.BranchSwitch -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    output.printError("Branch commands require --strategy branching")
                } else {
                    try {
                        val branch = bs.switchBranch(command.name)
                        output.printInfo("Switched to branch '${branch.name}' (${branch.messages.size} exchanges)")
                    } catch (e: IllegalArgumentException) {
                        output.printError(e.message ?: "Failed to switch branch")
                    }
                }
            }

            // ── Checkpoint commands ────────────────────────────────────────

            is Command.CheckpointList -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    output.printError("Checkpoint commands require --strategy branching")
                } else {
                    output.printCheckpointList(bs.listCheckpoints())
                }
            }

            is Command.CheckpointSave -> {
                val bs = strategy as? BranchingStrategy
                if (bs == null) {
                    output.printError("Checkpoint commands require --strategy branching")
                } else {
                    val cp = bs.saveCheckpoint(command.name)
                    output.printInfo(
                        "Checkpoint '${cp.name}' saved at message ${cp.messageCount} of branch '${bs.currentBranchName()}'."
                    )
                }
            }

            // ── Facts commands ─────────────────────────────────────────────

            is Command.FactsList -> {
                val fs = strategy as? StickyFactsStrategy
                if (fs == null) {
                    output.printError("Facts commands require --strategy sticky-facts")
                } else {
                    output.printFacts(fs.getFacts())
                }
            }

            is Command.FactsSet -> {
                val fs = strategy as? StickyFactsStrategy
                if (fs == null) {
                    output.printError("Facts commands require --strategy sticky-facts")
                } else {
                    fs.setFact(command.key, command.value)
                    output.printInfo("Fact set: ${command.key} = ${command.value}")
                }
            }

            is Command.FactsDelete -> {
                val fs = strategy as? StickyFactsStrategy
                if (fs == null) {
                    output.printError("Facts commands require --strategy sticky-facts")
                } else {
                    fs.deleteFact(command.key)
                    output.printInfo("Fact '${command.key}' removed.")
                }
            }

            is Command.Unknown -> {
                output.printError("Unknown command: ${command.input}")
                output.printInfo("Type /help for available commands.")
            }

            is Command.Message -> handleMessage(conversationManager, command.content, output, spinner, scope)
        }
    }

    inputReader.close()
}

suspend fun handleMessage(
    conversationManager: ConversationManager,
    message: String,
    output: CliOutput,
    spinner: ThinkingSpinner,
    scope: CoroutineScope
) {
    try {
        spinner.start(scope)

        val result = conversationManager.sendMessage(message)

        spinner.stop()

        result.fold(
            onSuccess = { stats ->
                output.printAssistantResponse(stats.response)
                output.printTokenStats(
                    inputTokens = stats.inputTokens,
                    windowTokens = stats.windowTokens,
                    summaryTokens = stats.summaryTokens,
                    responseTokens = stats.responseTokens,
                    totalTokens = stats.totalTokens
                )
            },
            onFailure = { error ->
                output.printError("Error communicating with LLM: ${error.message}")
                output.printInfo("Please try again or type /exit to quit.")
            }
        )
    } catch (e: Exception) {
        spinner.stop()
        output.printError("Unexpected error: ${e.message}")
        output.printInfo("Please try again or type /exit to quit.")
    }
}
