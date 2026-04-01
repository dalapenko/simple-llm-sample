package llmchat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.executor.ollama.client.ContextWindowStrategy
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import com.github.ajalt.mordant.terminal.Terminal
import indexer.chunker.FixedSizeChunker
import indexer.chunker.StructuralChunker
import indexer.document.DocumentLoader
import indexer.embedding.EmbeddingClient
import indexer.embedding.OllamaEmbeddingClient
import indexer.embedding.OpenRouterEmbeddingClient
import indexer.pipeline.IndexPipeline
import indexer.store.SqliteVectorStore
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
import llmchat.agent.strategy.chatSingleRunGraphStrategy
import llmchat.agent.task.TaskFSM
import llmchat.agent.task.TaskStage
import llmchat.agent.task.TaskStateStorage
import llmchat.cli.CliParser
import llmchat.cli.Command
import llmchat.cli.LlmProvider
import llmchat.cli.RagMode
import llmchat.cli.StrategyType
import llmchat.rag.AdvancedRagService
import llmchat.rag.ConversationalRagService
import llmchat.rag.RagPipeline
import llmchat.rag.RagService
import llmchat.support.SupportRunConfig
import llmchat.support.runSupportAgent
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
        if (config.provider == LlmProvider.OPENROUTER && apiKey.isNullOrBlank()) {
            output.printError("OPENROUTER_API_KEY environment variable is not set.")
            terminal.println("Please set your OpenRouter API key:")
            terminal.println("  export OPENROUTER_API_KEY='your-api-key-here'")
            terminal.println("Or use a local model with: --provider ollama")
            exitProcess(1)
        }

        runBlocking {
            if (config.isSupportMode) {
                val mcpJar = config.supportMcpJarPath
                    ?: "mcp-server-support/build/libs/mcp-server-support-1.0-SNAPSHOT-all.jar"
                val supportConfig = SupportRunConfig(
                    ticketId = config.supportTicketId!!,
                    apiKey = apiKey,
                    provider = config.provider,
                    localModelName = config.localModelName,
                    localUrl = config.localUrl,
                    localEmbeddingModel = config.localEmbeddingModel,
                    localContextLength = config.localContextLength,
                    temperature = config.temperature,
                    mcpJarPath = mcpJar
                )
                runSupportAgent(supportConfig, output)
            } else if (config.headlessMode) {
                startHeadlessCli(apiKey, config, output)
            } else {
                startInteractiveCli(apiKey, config, output, terminal, this)
            }
        }
    } catch (e: Exception) {
        output.printError("Fatal error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

suspend fun startInteractiveCli(
    apiKey: String?,
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

    // Resolve the embedding client and chat base URL based on the active provider.
    // For Ollama: embeddings use the native /api/embed endpoint; chat rewriting uses
    // the OpenAI-compatible /v1 endpoint exposed by Ollama.
    val embeddingClientForRag: EmbeddingClient? = when {
        config.ragMode == null -> null
        config.provider == LlmProvider.OLLAMA ->
            OllamaEmbeddingClient(config.localUrl, config.localEmbeddingModel)

        apiKey != null ->
            OpenRouterEmbeddingClient(apiKey)

        else -> {
            output.printError("RAG requires OPENROUTER_API_KEY when using the openrouter provider. Disabled.")
            null
        }
    }

    val chatBaseUrlForRag: String = when (config.provider) {
        LlmProvider.OLLAMA -> "${config.localUrl}/v1"
        LlmProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
    }

    val chatModelForRag: String = when (config.provider) {
        LlmProvider.OLLAMA -> config.localModelName
        LlmProvider.OPENROUTER -> config.model.openRouterModel.id
    }

    val ragService: RagPipeline? = when {
        config.ragMode == null || embeddingClientForRag == null -> null

        config.ragMode == RagMode.BASIC -> {
            val svc = RagService.create(embeddingClientForRag, topK = config.ragTopK)
            if (svc == null) {
                embeddingClientForRag.close()
                output.printError("RAG mode enabled but knowledge base not found. Run /index <path> first.")
            } else {
                output.printInfo("RAG (базовый) активен — знания загружены из ~/.llmchat/knowledge-base.db")
            }
            svc
        }

        config.ragMode == RagMode.ADVANCED -> {
            val svc = AdvancedRagService.create(
                embeddingClient = embeddingClientForRag,
                chatBaseUrl = chatBaseUrlForRag,
                model = chatModelForRag,
                apiKey = apiKey,
                similarityThreshold = config.similarityThreshold,
                finalTopK = config.ragTopK
            )
            if (svc == null) {
                embeddingClientForRag.close()
                output.printError("RAG mode enabled but knowledge base not found. Run /index <path> first.")
            } else {
                output.printInfo(
                    "RAG (продвинутый) активен — переписывание запросов + реранкинг" +
                            " | порог: ${config.similarityThreshold} | top-K: ${config.ragTopK}"
                )
            }
            svc
        }

        config.ragMode == RagMode.CONVERSATIONAL -> {
            val svc = ConversationalRagService.create(
                embeddingClient = embeddingClientForRag,
                chatBaseUrl = chatBaseUrlForRag,
                model = chatModelForRag,
                apiKey = apiKey,
                similarityThreshold = config.similarityThreshold,
                finalTopK = config.ragTopK
            )
            if (svc == null) {
                embeddingClientForRag.close()
                output.printError("RAG mode enabled but knowledge base not found. Run /index <path> first.")
            } else {
                output.printInfo(
                    "RAG (диалоговый) активен — история + состояние задачи + реранкинг" +
                            " | порог: ${config.similarityThreshold} | top-K: ${config.ragTopK}" +
                            " | /state — просмотр состояния | /reset — сброс памяти"
                )
            }
            svc
        }

        else -> null
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        inputReader.close()
        mcpManager.destroy()
        ragService?.close()
        taskFsm?.let { fsm ->
            if (fsm.getState().stage != TaskStage.DONE) {
                TaskStateStorage.save(fsm.getState())
            }
        }
        println("\nGoodbye!")
    })

    val promptExecutor = when (config.provider) {
        LlmProvider.OPENROUTER -> simpleOpenRouterExecutor(apiKey!!)
        LlmProvider.OLLAMA -> {
            // When a custom context length is requested, create OllamaClient directly so that
            // ContextWindowStrategy.Fixed sends the correct num_ctx in every chat request.
            // With ContextWindowStrategy.None (the default), Ollama uses its own default (2048)
            // unless the model was started with a different OLLAMA_CONTEXT_LENGTH env var.
            val client = if (config.localContextLength != null) {
                OllamaClient(
                    baseUrl = config.localUrl,
                    contextWindowStrategy = ContextWindowStrategy.Companion.Fixed(config.localContextLength.toLong())
                )
            } else {
                OllamaClient(baseUrl = config.localUrl)
            }
            SingleLLMPromptExecutor(client)
        }
    }

    val llmModel = when (config.provider) {
        LlmProvider.OPENROUTER -> config.model.openRouterModel
        LlmProvider.OLLAMA -> LLModel(
            provider = LLMProvider.Ollama,
            id = config.localModelName,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Completion,
                LLMCapability.Tools
            ),
            // contextLength here informs Koog's token-budget calculations and acts as the
            // upper bound for ContextWindowStrategy.Fixed. Match it to localContextLength
            // when set; otherwise fall back to a conservative 8 192-token default.
            contextLength = (config.localContextLength ?: 8_192).toLong()
        )
    }

    val agentFactory: (String, ToolRegistry) -> AIAgent<String, String> = { systemPrompt, toolRegistry ->
        // Use AIAgentConfig when maxTokens is configured so that LLMParams.maxTokens is
        // forwarded to the executor (the simplified AIAgent constructor does not expose it).
        if (config.localMaxTokens != null) {
            val agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "chat",
                    params = LLMParams(
                        temperature = config.temperature,
                        maxTokens = config.localMaxTokens
                    )
                ) {
                    system(systemPrompt)
                },
                model = llmModel,
                maxAgentIterations = 50,
            )
            AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                strategy = chatSingleRunGraphStrategy(),
                toolRegistry = toolRegistry,
            ) {
                install(Tracing) {
                    addMessageProcessor(McpToolCallDisplayProcessor(output))
                }
            }
        } else {
            AIAgent(
                promptExecutor = promptExecutor,
                llmModel = llmModel,
                strategy = chatSingleRunGraphStrategy(),
                systemPrompt = systemPrompt,
                temperature = config.temperature,
                toolRegistry = toolRegistry,
            ) {
                install(Tracing) {
                    addMessageProcessor(McpToolCallDisplayProcessor(output))
                }
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

            is Command.Help -> {
                if (ragService != null) {
                    val helpQuery = "Дай полный обзор этого проекта: что он делает, как устроен, " +
                            "основные возможности и команды. Если доступны git-инструменты — " +
                            "проверь текущую ветку и состояние репозитория."
                    val ragResult = ragService.augment(helpQuery)
                    output.printRagInfo(ragResult)
                    if (!ragResult.lowRelevance) {
                        handleMessage(conversationManager, ragResult.augmentedMessage, output, spinner, scope)
                    } else {
                        output.printInteractiveHelp(config.strategyType)
                    }
                } else {
                    output.printInteractiveHelp(config.strategyType)
                }
            }

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

            // ── Index commands ─────────────────────────────────────────────

            is Command.Index -> {
                val canIndex = config.provider == LlmProvider.OLLAMA || apiKey != null
                if (!canIndex) {
                    output.printError("/index requires OPENROUTER_API_KEY when using the openrouter provider.")
                } else {
                    val directory = File(command.path)
                    if (!directory.isDirectory) {
                        output.printError("Not a directory: ${command.path}")
                    } else {
                        val chunker = when (command.strategy) {
                            "fixed" -> FixedSizeChunker()
                            else -> StructuralChunker()
                        }
                        val dbPath = System.getProperty("user.home") + "/.llmchat/knowledge-base.db"
                        File(dbPath).parentFile.mkdirs()
                        val store = SqliteVectorStore(dbPath)
                        val embeddingClient: EmbeddingClient = when (config.provider) {
                            LlmProvider.OLLAMA ->
                                OllamaEmbeddingClient(config.localUrl, config.localEmbeddingModel)

                            LlmProvider.OPENROUTER ->
                                OpenRouterEmbeddingClient(apiKey!!)
                        }

                        val pipeline = IndexPipeline(
                            loader = DocumentLoader(),
                            chunker = chunker,
                            embeddingClient = embeddingClient,
                            store = store
                        )

                        val providerLabel = when (config.provider) {
                            LlmProvider.OLLAMA -> "ollama/${config.localEmbeddingModel}"
                            LlmProvider.OPENROUTER -> "openrouter/text-embedding-3-small"
                        }
                        spinner.start(
                            scope,
                            label = "Indexing ${command.path} [${command.strategy}] via $providerLabel..."
                        )
                        try {
                            val report = pipeline.run(directory, withComparison = command.report)
                            spinner.stop()
                            output.printIndexReport(report)
                        } catch (e: Exception) {
                            spinner.stop()
                            output.printError("Indexing failed: ${e.message}")
                        } finally {
                            embeddingClient.close()
                            store.close()
                        }
                    }
                }
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

            is Command.RagReset -> {
                val convSvc = ragService as? ConversationalRagService
                if (convSvc == null) {
                    output.printError("/reset is only available in --mode conversational")
                } else {
                    convSvc.reset()
                    conversationManager.clearHistory()
                    conversationManager.setRagTaskStateBlock("")
                    output.printInfo("Диалоговая память и состояние задачи сброшены.")
                }
            }

            is Command.RagState -> {
                val convSvc = ragService as? ConversationalRagService
                if (convSvc == null) {
                    output.printError("/state is only available in --mode conversational")
                } else {
                    terminal.println(convSvc.taskStateManager.toDisplayString())
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
                var lowRelevanceTriggered = false
                val messageToSend = if (ragService != null) {
                    val ragResult = ragService.augment(command.content)
                    output.printRagInfo(ragResult)
                    if (ragResult.lowRelevance) {
                        output.printLowRelevanceResponse(ragResult.augmentedMessage)
                        lowRelevanceTriggered = true
                    }
                    ragResult.augmentedMessage
                } else {
                    command.content
                }
                if (lowRelevanceTriggered) continue
                val stats = handleMessage(conversationManager, messageToSend, output, spinner, scope) {
                    while (pendingNotifications.isNotEmpty()) {
                        val (t, d) = pendingNotifications.poll() ?: break
                        inputReader.lineReader.printAbove(output.buildMcpNotificationBanner(t, d))
                    }
                }
                // Update conversational RAG history and task state after each successful turn
                val convSvc = ragService as? ConversationalRagService
                if (stats != null && convSvc != null) {
                    convSvc.addToHistory(command.content, stats.response)
                    convSvc.taskStateManager.updateFromTurn(command.content, stats.response)
                    conversationManager.setRagTaskStateBlock(convSvc.taskStateManager.toSystemPromptBlock())
                }
                val proposal = stats?.transitionProposal
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

private const val PR_REVIEW_SYSTEM_PROMPT = """You are an expert Kotlin code reviewer performing a pull request review.
Analyze the provided git diff carefully and produce a structured review.

Focus on:
1. Potential Bugs — logic errors, null-safety issues (Kotlin NPE risks), unhandled edge cases
2. Architectural Consistency — do the changes align with existing patterns, naming conventions, and module structure?
3. Security Concerns — input validation, injection risks, credential exposure, improper error handling
4. Kotlin Best Practices — proper use of coroutines, sealed classes, extension functions, idiomatic Kotlin
5. Improvement Suggestions — cleaner abstractions, better use of the standard library

Output format — use these Markdown sections:
## Critical Issues
## High Priority
## Medium Priority
## Suggestions
## Summary

For each finding state the file and approximate line from the diff, describe the issue concisely, and provide a concrete fix.
If no issues are found in a category write "None identified."
Do NOT describe what the code does. Focus solely on problems and improvements."""

private const val MAX_DIFF_CHARS = 30_000

suspend fun startHeadlessCli(
    apiKey: String?,
    config: llmchat.cli.CliConfig,
    output: CliOutput,
) {
    // Build RAG index from a local path if requested (CI cache-miss path).
    if (config.buildIndexPath != null) {
        val indexDir = File(config.buildIndexPath)
        val dbPath = System.getProperty("user.home") + "/.llmchat/knowledge-base.db"
        if (!File(dbPath).exists()) {
            System.err.println("[headless] Building RAG index from: ${indexDir.absolutePath}")
            val canIndex = config.provider == LlmProvider.OLLAMA || apiKey != null
            if (!canIndex) {
                System.err.println("[headless] WARNING: Cannot build index without OPENROUTER_API_KEY. Skipping.")
            } else if (!indexDir.isDirectory) {
                System.err.println("[headless] WARNING: --build-index path is not a directory: ${config.buildIndexPath}. Skipping.")
            } else {
                val embeddingClient: EmbeddingClient = when (config.provider) {
                    LlmProvider.OLLAMA -> OllamaEmbeddingClient(config.localUrl, config.localEmbeddingModel)
                    LlmProvider.OPENROUTER -> OpenRouterEmbeddingClient(apiKey!!)
                }
                File(dbPath).parentFile.mkdirs()
                val store = SqliteVectorStore(dbPath)
                val pipeline = IndexPipeline(
                    loader = DocumentLoader(),
                    chunker = StructuralChunker(),
                    embeddingClient = embeddingClient,
                    store = store
                )
                try {
                    val report = pipeline.run(indexDir, withComparison = false)
                    System.err.println("[headless] Index complete: ${report.primaryStrategy.totalChunks} chunks from ${report.filesProcessed} files")
                } catch (e: Exception) {
                    System.err.println("[headless] WARNING: Indexing failed: ${e.message}. Proceeding without RAG.")
                } finally {
                    embeddingClient.close()
                    store.close()
                }
            }
        } else {
            System.err.println("[headless] RAG index already exists — skipping build.")
        }
    }

    // Read diff content from file or stdin.
    val diffContent = if (config.diffFilePath != null) {
        val file = File(config.diffFilePath)
        if (!file.exists()) {
            System.err.println("ERROR: Diff file not found: ${config.diffFilePath}")
            exitProcess(1)
        }
        file.readText()
    } else {
        System.`in`.bufferedReader().readText()
    }

    if (diffContent.isBlank()) {
        System.err.println("ERROR: No diff content provided. Use --diff-file <path> or pipe via stdin.")
        exitProcess(1)
    }

    // Initialize RAG (optional, same pattern as interactive mode).
    val embeddingClientForRag: EmbeddingClient? = when {
        config.ragMode == null -> null
        config.provider == LlmProvider.OLLAMA ->
            OllamaEmbeddingClient(config.localUrl, config.localEmbeddingModel)

        apiKey != null ->
            OpenRouterEmbeddingClient(apiKey)

        else -> {
            System.err.println("[headless] WARNING: RAG requires OPENROUTER_API_KEY. Proceeding without RAG.")
            null
        }
    }

    val ragService: RagPipeline? = if (embeddingClientForRag != null) {
        val svc = RagService.create(embeddingClientForRag, topK = config.ragTopK)
        if (svc == null) {
            embeddingClientForRag.close()
            System.err.println("[headless] WARNING: Knowledge base not found. Run --build-index or /index first. Proceeding without RAG.")
            null
        } else {
            System.err.println("[headless] RAG active — knowledge base loaded.")
            svc
        }
    } else null

    // Build executor and model (same as interactive, no TTY dependency).
    val promptExecutor = when (config.provider) {
        LlmProvider.OPENROUTER -> simpleOpenRouterExecutor(apiKey!!)
        LlmProvider.OLLAMA -> {
            val client = if (config.localContextLength != null) {
                OllamaClient(
                    baseUrl = config.localUrl,
                    contextWindowStrategy = ContextWindowStrategy.Companion.Fixed(config.localContextLength.toLong())
                )
            } else {
                OllamaClient(baseUrl = config.localUrl)
            }
            SingleLLMPromptExecutor(client)
        }
    }

    val llmModel = when (config.provider) {
        LlmProvider.OPENROUTER -> config.model.openRouterModel
        LlmProvider.OLLAMA -> LLModel(
            provider = LLMProvider.Ollama,
            id = config.localModelName,
            capabilities = listOf(LLMCapability.Temperature, LLMCapability.Completion, LLMCapability.Tools),
            contextLength = (config.localContextLength ?: 8_192).toLong()
        )
    }

    // Use the custom --system-prompt if provided, otherwise fall back to the built-in review prompt.
    val reviewSystemPrompt =
        if (config.systemPrompt == "You are a helpful assistant. Answer user questions concisely.") {
            PR_REVIEW_SYSTEM_PROMPT
        } else {
            config.systemPrompt
        }

    val agentFactory: (String, ToolRegistry) -> AIAgent<String, String> = { systemPrompt, toolRegistry ->
        AIAgent(
            promptExecutor = promptExecutor,
            llmModel = llmModel,
            strategy = chatSingleRunGraphStrategy(),
            systemPrompt = systemPrompt,
            temperature = config.temperature,
            toolRegistry = toolRegistry,
        )
    }

    val conversationManager = ConversationManager(
        agentFactory = agentFactory,
        strategy = SlidingWindowStrategy(windowSize = 1),
        profileManager = ProfileManager(),
        invariantStorage = InvariantStorage()
    )
    conversationManager.setBaseSystemPrompt(reviewSystemPrompt)

    // Build user message — truncate very large diffs to stay within the model's context window.
    val truncated = diffContent.length > MAX_DIFF_CHARS
    val diffBody = diffContent.take(MAX_DIFF_CHARS)
    val userMessage = buildString {
        append("Review the following pull request diff:\n\n```diff\n")
        append(diffBody)
        if (truncated) append("\n\n... [diff truncated at $MAX_DIFF_CHARS characters — only the first part was reviewed]")
        append("\n```")
    }

    val messageToSend = if (ragService != null) {
        val ragResult = ragService.augment(userMessage)
        System.err.println("[headless] RAG augmented with ${ragResult.chunksFound} context chunks.")
        ragResult.augmentedMessage
    } else {
        userMessage
    }

    val result = conversationManager.sendMessage(messageToSend)

    ragService?.close()

    result.fold(
        onSuccess = { stats ->
            output.printPlain(stats.response)
            exitProcess(0)
        },
        onFailure = { error ->
            System.err.println("ERROR: LLM call failed: ${error.message}")
            exitProcess(1)
        }
    )
}

suspend fun handleMessage(
    conversationManager: ConversationManager,
    message: String,
    output: CliOutput,
    spinner: ThinkingSpinner,
    scope: CoroutineScope,
    onSpinnerStopped: () -> Unit = {}
): llmchat.agent.RequestStatistics? {
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
                stats
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
