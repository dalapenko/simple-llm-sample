package llmchat.support

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import indexer.chunker.StructuralChunker
import indexer.document.DocumentLoader
import indexer.embedding.EmbeddingClient
import indexer.embedding.OllamaEmbeddingClient
import indexer.embedding.OpenRouterEmbeddingClient
import indexer.pipeline.IndexPipeline
import indexer.store.SqliteVectorStore
import llmchat.agent.ConversationManager
import llmchat.agent.context.SlidingWindowStrategy
import llmchat.agent.invariant.InvariantStorage
import llmchat.agent.mcp.McpConnectionManager
import llmchat.agent.profile.ProfileManager
import llmchat.agent.strategy.chatSingleRunGraphStrategy
import llmchat.cli.LlmProvider
import llmchat.rag.RagService
import llmchat.ui.CliOutput
import llmchat.ui.McpToolCallDisplayProcessor
import java.io.File

internal const val SUPPORT_SYSTEM_PROMPT = """You are a compassionate and professional customer support assistant.

ROLE CONSTRAINTS (strictly enforced):
- You represent the support team. You are read-only — you cannot make changes to accounts or systems.
- You MUST call the tool get_ticket_context BEFORE responding to any query that involves a specific ticket or account issue.
- You MUST NOT reveal other users' data or promise outcomes you cannot guarantee.
- Respond in Russian unless the user writes in another language.

BEHAVIORAL RULES:
- Address the user by their first name if available from ticket context.
- Acknowledge the user's frustration or inconvenience before providing technical information.
- For LOCKED accounts: explain the lock and offer to verify identity to unlock the account.
- For SUSPENDED accounts: explain that suspension requires billing team review. DO NOT attempt to unlock — escalate only.
- For OVERDUE payment: direct the user to Billing → Update Payment Method. Do not access billing data directly.
- If you cannot resolve an issue: clearly state what you can do, offer escalation, set expectations on timeline.

EMPATHY GUIDELINES:
- Open with a brief empathetic acknowledgment when the user expresses frustration or urgency.
- Use a warm, professional tone. Not overly formal, not casual.
- Validate that the problem is real and important.
- Close each response with a clear next step or offer for follow-up.

RESPONSE FORMAT (Russian):
- Keep responses concise: 3–5 short paragraphs maximum.
- Provide numbered steps when giving instructions.
- Always call get_ticket_context first when a ticket ID is mentioned."""

data class SupportRunConfig(
    val ticketId: String,
    val apiKey: String?,
    val provider: LlmProvider,
    val localModelName: String,
    val localUrl: String,
    val localEmbeddingModel: String,
    val localContextLength: Int?,
    val temperature: Double,
    val mcpJarPath: String
)

/**
 * Runs a single-turn support agent:
 * 1. Builds support FAQ RAG index (if not already present)
 * 2. Starts the MCP support server as a subprocess
 * 3. Sends the ticket ID query to the agent
 * 4. Prints the empathetic response
 */
suspend fun runSupportAgent(
    config: SupportRunConfig,
    output: CliOutput
) {
    val homeDir = System.getProperty("user.home")
    val supportDbPath = "$homeDir/.llmchat/support-data/support-knowledge.db"
    val supportDataJson = "$homeDir/.llmchat/support-data/users_tickets.json"

    // ── 1. Build embedding client ─────────────────────────────────────────────
    val embeddingClient: EmbeddingClient? = when (config.provider) {
        LlmProvider.OLLAMA -> OllamaEmbeddingClient(config.localUrl, config.localEmbeddingModel)
        LlmProvider.OPENROUTER -> if (config.apiKey != null) OpenRouterEmbeddingClient(config.apiKey) else null
    }

    // ── 2. Build or reuse the support-specific RAG index ─────────────────────
    if (embeddingClient != null && !File(supportDbPath).exists()) {
        val faqDir = File("docs/support-faq")
        if (faqDir.isDirectory) {
            output.printInfo("Building support FAQ index from ${faqDir.absolutePath}...")
            File(supportDbPath).parentFile.mkdirs()
            val store = SqliteVectorStore(supportDbPath)
            val pipeline = IndexPipeline(
                loader = DocumentLoader(),
                chunker = StructuralChunker(),
                embeddingClient = embeddingClient,
                store = store
            )
            try {
                val report = pipeline.run(faqDir, withComparison = false)
                output.printInfo("Support FAQ indexed: ${report.primaryStrategy.totalChunks} chunks from ${report.filesProcessed} files")
            } catch (e: Exception) {
                output.printError("Support FAQ indexing failed: ${e.message}")
            } finally {
                store.close()
            }
        } else {
            output.printInfo("Support FAQ directory not found at ${faqDir.absolutePath} — RAG disabled.")
        }
    }

    val ragService = if (embeddingClient != null) {
        RagService.create(embeddingClient, dbPath = supportDbPath)?.also {
            output.printInfo("Support RAG active — FAQ knowledge loaded.")
        }
    } else null

    // ── 3. Build prompt executor and LLM model ────────────────────────────────
    val promptExecutor = when (config.provider) {
        LlmProvider.OPENROUTER -> simpleOpenRouterExecutor(config.apiKey!!)
        LlmProvider.OLLAMA -> SingleLLMPromptExecutor(OllamaClient(baseUrl = config.localUrl))
    }

    val llmModel = when (config.provider) {
        LlmProvider.OPENROUTER -> llmchat.model.SupportedModel.default.openRouterModel
        LlmProvider.OLLAMA -> LLModel(
            provider = LLMProvider.Ollama,
            id = config.localModelName,
            capabilities = listOf(LLMCapability.Temperature, LLMCapability.Completion, LLMCapability.Tools),
            contextLength = (config.localContextLength ?: 8_192).toLong()
        )
    }

    // ── 4. Start support MCP server as subprocess ─────────────────────────────
    val mcpJarFile = File(config.mcpJarPath)
    if (!mcpJarFile.exists()) {
        output.printError(
            "Support MCP server JAR not found: ${mcpJarFile.absolutePath}\n" +
                    "Run: ./gradlew :mcp-server-support:shadowJar"
        )
        ragService?.close()
        embeddingClient?.close()
        return
    }

    val mcpManager = McpConnectionManager()
    val (mcpInfo, mcpRegistry) = try {
        mcpManager.connect(
            command = "java",
            args = listOf("-jar", config.mcpJarPath, supportDataJson)
        )
    } catch (e: Exception) {
        output.printError("Failed to start support MCP server: ${e.message}")
        ragService?.close()
        embeddingClient?.close()
        return
    }
    output.printInfo("Support CRM MCP connected: ${mcpInfo.commandLine} (${mcpRegistry.tools.size} tools)")

    // ── 5. Build agent factory ────────────────────────────────────────────────
    val agentFactory: (String, ToolRegistry) -> AIAgent<String, String> = { systemPrompt, toolRegistry ->
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

    // ── 6. Build ConversationManager ──────────────────────────────────────────
    val conversationManager = ConversationManager(
        agentFactory = agentFactory,
        strategy = SlidingWindowStrategy(windowSize = 10),
        profileManager = ProfileManager(),
        invariantStorage = InvariantStorage()
    )
    conversationManager.setBaseSystemPrompt(SUPPORT_SYSTEM_PROMPT)
    conversationManager.setMcpToolRegistry(mcpRegistry, mcpInfo)

    // ── 7. Build user message with RAG augmentation ───────────────────────────
    // The explicit instruction to call get_ticket_context MUST appear in the user message
    // regardless of RAG, so the agent actually invokes the MCP tool before answering.
    val userQuery = "Вызови get_ticket_context для тикета ${config.ticketId}, " +
            "затем ответь на вопрос пользователя: почему не работает авторизация?"
    val messageToSend = if (ragService != null) {
        val ragResult = ragService.augment("authorization failure account suspended locked 403 401")
        output.printRagInfo(ragResult)
        // Append FAQ context after the explicit tool-call instruction
        "$userQuery\n\n${ragResult.augmentedMessage}"
    } else {
        userQuery
    }

    // ── 8. Single-turn support response ──────────────────────────────────────
    output.printInfo("Running support agent for ticket: ${config.ticketId}...")
    val result = conversationManager.sendMessage(messageToSend)

    result.fold(
        onSuccess = { stats ->
            output.printAssistantResponse(stats.response)
        },
        onFailure = { error ->
            output.printError("Support agent error: ${error.message}")
        }
    )

    // ── 9. Cleanup ────────────────────────────────────────────────────────────
    mcpManager.destroy()
    ragService?.close()
    embeddingClient?.close()
}
