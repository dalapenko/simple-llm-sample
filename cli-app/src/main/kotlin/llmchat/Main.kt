package llmchat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import llmchat.agent.ConversationManager
import llmchat.agent.ConversationStorage
import llmchat.agent.context.BranchingStrategy
import llmchat.agent.context.ContextStrategy
import llmchat.agent.context.LayeredMemoryStrategy
import llmchat.agent.context.SlidingWindowStrategy
import llmchat.agent.context.StickyFactsStrategy
import llmchat.agent.invariant.InvariantStorage
import llmchat.agent.mcp.McpConnectionManager
import llmchat.agent.memory.MemoryLayer
import llmchat.agent.profile.ProfileManager
import llmchat.agent.task.TaskFSM
import llmchat.agent.task.TaskStage
import llmchat.agent.task.TaskStateStorage
import llmchat.agent.task.TaskTransitionProposal
import llmchat.cli.CliParser
import llmchat.cli.Command
import llmchat.cli.StrategyType
import llmchat.ui.ChatInputReader
import llmchat.ui.CliOutput
import llmchat.ui.McpToolCallDisplayProcessor
import llmchat.ui.ThinkingSpinner
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
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
    val mcpManager = McpConnectionManager()
    val pendingNotifications = ConcurrentLinkedQueue<Pair<String, String>>()
    var taskFsm: TaskFSM? = null

    Runtime.getRuntime().addShutdownHook(Thread {
        inputReader.close()
        mcpManager.destroy()
        taskFsm?.let { fsm ->
            if (fsm.getState().stage != TaskStage.DONE) {
                TaskStateStorage.save(fsm.getState())
            }
        }
        println("\nGoodbye!")
    })

    val promptExecutor = simpleOpenRouterExecutor(apiKey)

    val agentFactory: (String, ToolRegistry) -> AIAgent<String, String> = { systemPrompt, toolRegistry ->
        AIAgent(
            promptExecutor = promptExecutor,
            systemPrompt = systemPrompt,
            llmModel = config.model.openRouterModel,
            temperature = config.temperature,
            toolRegistry = toolRegistry
        ) {
            install(Tracing) {
                addMessageProcessor(McpToolCallDisplayProcessor(output))
            }
        }
    }

    val strategy: ContextStrategy = when (config.strategyType) {
        StrategyType.SLIDING_WINDOW ->
            SlidingWindowStrategy(config.contextWindow.windowSize)

        StrategyType.STICKY_FACTS ->
            StickyFactsStrategy(
                windowSize = config.contextWindow.windowSize,
                agentFactory = { systemPrompt -> agentFactory(systemPrompt, ToolRegistry.EMPTY) }
            )

        StrategyType.BRANCHING ->
            BranchingStrategy(config.contextWindow.windowSize)

        StrategyType.LAYERED ->
            LayeredMemoryStrategy(config.contextWindow.windowSize)
    }

    val profileManager = if (config.profilePath != null) {
        val file = File(config.profilePath)
        output.printInfo("Using profile: ${file.absolutePath}")
        ProfileManager(file, writeDefaultIfMissing = false)
    } else {
        ProfileManager()
    }

    val invariantStorage = InvariantStorage()

    val conversationManager = ConversationManager(
        agentFactory = agentFactory,
        strategy = strategy,
        profileManager = profileManager,
        invariantStorage = invariantStorage
    )
    conversationManager.setBaseSystemPrompt(config.systemPrompt)

    // Task state resume
    if (TaskStateStorage.hasActiveState()) {
        val savedState = TaskStateStorage.load()
        if (savedState != null) {
            output.printTaskResume(savedState)
            val answer = inputReader.readInput(
                terminal.theme.style("prompt")(" Resume task? [y/N]: ")
                    .ifEmpty { " Resume task? [y/N]: " }
            )?.trim()?.lowercase()
            if (answer == "y" || answer == "yes") {
                taskFsm = TaskFSM(savedState)
                conversationManager.setTaskFsm(taskFsm)
            } else {
                TaskStateStorage.clear()
            }
        }
    }

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

    // When any MCP server emits "[NOTIFY] title\tdescription" on stderr, show a generic banner.
    // If the spinner is active (LLM is thinking), buffer the notification and show it
    // immediately after the spinner stops — avoids raw-print / JLine redraw conflicts.
    mcpManager.setNotificationHandler { title, description ->
        val banner = output.buildMcpNotificationBanner(title, description)
        if (spinner.isRunning) {
            pendingNotifications.add(Pair(title, description))
        } else {
            inputReader.lineReader.printAbove(banner)
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

            // ── Memory commands ────────────────────────────────────────────

            is Command.MemoryAdd -> {
                val lms = strategy as? LayeredMemoryStrategy
                if (lms == null) {
                    output.printError("Memory commands require --strategy layered")
                } else {
                    val item = lms.addToLayer(command.layer, command.data)
                    output.printInfo("Added [${item.id}] to ${command.layer.displayName}.")
                }
            }

            is Command.MemoryList -> {
                val lms = strategy as? LayeredMemoryStrategy
                if (lms == null) {
                    output.printError("Memory commands require --strategy layered")
                } else {
                    val layers = if (command.layer != null) listOf(command.layer) else MemoryLayer.entries
                    val itemsByLayer = layers.associateWith { lms.listLayer(it) }
                    output.printMemoryList(command.layer, itemsByLayer)
                }
            }

            is Command.MemoryDelete -> {
                val lms = strategy as? LayeredMemoryStrategy
                if (lms == null) {
                    output.printError("Memory commands require --strategy layered")
                } else {
                    val removed = lms.deleteFromLayer(command.layer, command.id)
                    if (removed) output.printInfo("Deleted [${command.id}] from ${command.layer.displayName}.")
                    else output.printError("No item with id '${command.id}' in ${command.layer.displayName}.")
                }
            }

            is Command.MemoryClear -> {
                val lms = strategy as? LayeredMemoryStrategy
                if (lms == null) {
                    output.printError("Memory commands require --strategy layered")
                } else {
                    lms.clearLayer(command.layer)
                    output.printInfo("${command.layer.displayName} cleared.")
                }
            }

            // ── Task commands ──────────────────────────────────────────────

            is Command.TaskStart -> {
                if (taskFsm != null) {
                    output.printError("A task is already active. Use /task done or /task cancel first.")
                } else {
                    val newFsm = TaskFSM.create(command.description)
                    taskFsm = newFsm
                    conversationManager.setTaskFsm(newFsm)
                    output.printTaskStatus(newFsm.getState())
                }
            }

            is Command.TaskStatus -> {
                val fsm = taskFsm
                if (fsm == null) output.printInfo("No active task. Use /task start <description> to begin.")
                else output.printTaskStatus(fsm.getState())
            }

            is Command.TaskPause -> {
                val fsm = taskFsm
                if (fsm == null) {
                    output.printInfo("No active task.")
                } else {
                    TaskStateStorage.save(fsm.getState())
                    output.printInfo("Task paused and saved. It will be offered for resume on next startup.")
                }
            }

            is Command.TaskDone -> {
                val fsm = taskFsm
                if (fsm == null) {
                    output.printInfo("No active task.")
                } else {
                    val prevStage = fsm.getState().stage
                    fsm.transition(TaskStage.DONE, "Completed").fold(
                        onSuccess = {
                            output.printTaskTransition(prevStage, TaskStage.DONE)
                            TaskStateStorage.clear()
                            taskFsm = null
                            conversationManager.setTaskFsm(null)
                            output.printInfo("Task completed and state cleared.")
                        },
                        onFailure = { e -> output.printError(e.message ?: "Transition failed") }
                    )
                }
            }

            is Command.TaskCancel -> {
                if (taskFsm == null) {
                    output.printInfo("No active task.")
                } else {
                    TaskStateStorage.clear()
                    taskFsm = null
                    conversationManager.setTaskFsm(null)
                    output.printInfo("Task cancelled and state cleared.")
                }
            }

            is Command.TaskAdvance -> {
                val fsm = taskFsm
                if (fsm == null) {
                    output.printError("No active task. Use /task start <description> first.")
                } else {
                    val prevStage = fsm.getState().stage
                    fsm.transition(command.stage).fold(
                        onSuccess = { state ->
                            output.printTaskTransition(prevStage, command.stage)
                            output.printTaskStatus(state)
                        },
                        onFailure = { e -> output.printError(e.message ?: "Transition failed") }
                    )
                }
            }

            is Command.TaskStep -> {
                val fsm = taskFsm
                if (fsm == null) {
                    output.printError("No active task.")
                } else {
                    val action = command.action ?: fsm.getState().expectedAction
                    val state = fsm.updateStep(command.description, action)
                    output.printInfo("Step updated: ${state.currentStep}")
                }
            }

            // ── Invariant commands ─────────────────────────────────────────

            is Command.InvariantAdd -> {
                val inv = invariantStorage.add(command.description, command.category)
                output.printInfo("Invariant added: [${inv.id}] [${inv.category.displayName}] ${inv.description}")
                if (!invariantStorage.isEmpty()) {
                    output.printInfo("Active invariants: ${invariantStorage.list().size} — injected into every request.")
                }
            }

            is Command.InvariantList -> output.printInvariants(invariantStorage.list())

            is Command.InvariantRemove -> {
                val removed = invariantStorage.remove(command.id)
                if (removed) output.printInfo("Invariant [${command.id}] removed.")
                else output.printError("No invariant with id '${command.id}'.")
            }

            is Command.InvariantClear -> {
                invariantStorage.clear()
                output.printInfo("All invariants cleared.")
            }

            // ── Profile commands ───────────────────────────────────────────

            is Command.ProfileShow -> output.printProfileStatus(profileManager)

            is Command.ProfilePath -> output.printInfo("Profile file: ${profileManager.filePath()}")

            is Command.ProfileReload -> {
                profileManager.reload()
                val status = if (profileManager.getProfile() != null) "loaded" else "not active (file empty or missing)"
                output.printInfo("Profile reloaded — $status.")
            }

            // ── MCP commands ───────────────────────────────────────────────

            is Command.McpConnect -> {
                spinner.start(scope, label = "Connecting to MCP server...")
                try {
                    val (info, registry) = mcpManager.connect(command.command, command.args)
                    spinner.stop()
                    conversationManager.setMcpToolRegistry(registry, info)
                    output.printMcpConnected(info, registry.tools.size, mcpManager.getMergedRegistry().tools.size)
                } catch (e: Exception) {
                    spinner.stop()
                    output.printError("MCP connect failed: ${e.message}")
                    output.printInfo("Check that the command is installed and accessible.")
                }
            }

            is Command.McpTools -> {
                val registry = mcpManager.getRegistry()
                if (registry == null) {
                    output.printError("Not connected to an MCP server. Use /mcp connect <command> first.")
                } else {
                    output.printMcpTools(registry.tools)
                }
            }

            is Command.McpStatus -> output.printMcpStatus(mcpManager.getConnections())

            is Command.McpDisconnect -> {
                if (!mcpManager.isConnected) {
                    output.printInfo("No MCP server connected.")
                } else {
                    mcpManager.disconnect()
                    conversationManager.clearMcpToolRegistry()
                    output.printMcpDisconnected()
                }
            }

            is Command.Unknown -> {
                output.printError("Unknown command: ${command.input}")
                output.printInfo("Type /help for available commands.")
            }

            is Command.TaskAuto -> {
                if (taskFsm == null) {
                    output.printError("No active task. Use /task start <description> first.")
                } else if (command.enabled == null) {
                    output.printAutoMode(conversationManager.isAutoMode())
                } else {
                    conversationManager.setAutoMode(command.enabled)
                    output.printAutoMode(command.enabled)
                }
            }

            is Command.Message -> {
                val proposal = handleMessage(conversationManager, command.content, output, spinner, scope) {
                    while (pendingNotifications.isNotEmpty()) {
                        val (t, d) = pendingNotifications.poll() ?: break
                        inputReader.lineReader.printAbove(output.buildMcpNotificationBanner(t, d))
                    }
                }
                val fsm = taskFsm
                if (proposal != null && fsm != null) {
                    val requiresApproval = proposal.targetStage.requiredApproval ==
                            llmchat.agent.task.ExpectedAction.USER_APPROVAL
                    output.printTransitionProposal(proposal, requiresApproval)

                    val proceed = if (requiresApproval) {
                        val answer = inputReader.readInput(
                            terminal.theme.style("prompt")(" Apply transition? [y/N]: ")
                                .ifEmpty { " Apply transition? [y/N]: " }
                        )?.trim()?.lowercase()
                        answer == "y" || answer == "yes"
                    } else {
                        true
                    }

                    if (proceed) {
                        val prevStage = fsm.getState().stage
                        fsm.transition(proposal.targetStage, proposal.step).fold(
                            onSuccess = { state ->
                                output.printTaskTransition(prevStage, proposal.targetStage)
                                output.printTaskStatus(state)
                            },
                            onFailure = { e -> output.printError(e.message ?: "Transition failed") }
                        )
                    } else {
                        output.printInfo("Transition rejected.")
                    }
                }
            }
        }
    }

    inputReader.close()
}

suspend fun handleMessage(
    conversationManager: ConversationManager,
    message: String,
    output: CliOutput,
    spinner: ThinkingSpinner,
    scope: CoroutineScope,
    onSpinnerStopped: () -> Unit = {}
): TaskTransitionProposal? {
    return try {
        spinner.start(scope)

        val result = conversationManager.sendMessage(message)

        spinner.stop()
        onSpinnerStopped()

        result.fold(
            onSuccess = { stats ->
                output.printAssistantResponse(stats.response)
                output.printTokenStats(
                    inputTokens = stats.inputTokens,
                    windowTokens = stats.windowTokens,
                    summaryTokens = stats.summaryTokens,
                    responseTokens = stats.responseTokens,
                    totalTokens = stats.totalTokens,
                    longTermTokens = stats.longTermTokens
                )
                stats.transitionProposal
            },
            onFailure = { error ->
                output.printError("Error communicating with LLM: ${error.message}")
                output.printInfo("Please try again or type /exit to quit.")
                null
            }
        )
    } catch (e: Exception) {
        spinner.stop()
        onSpinnerStopped()
        output.printError("Unexpected error: ${e.message}")
        output.printInfo("Please try again or type /exit to quit.")
        null
    }
}
