package llmchat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import kotlinx.coroutines.runBlocking
import llmchat.agent.ConversationManager
import llmchat.agent.ConversationStorage
import llmchat.cli.CliParser
import llmchat.cli.Command
import llmchat.ui.CliOutput
import kotlin.system.exitProcess

/**
 * Main entry point for the LLM Chat CLI application.
 */
fun main(args: Array<String>) {
    try {
        // Parse command-line arguments
        val config = try {
            CliParser.parse(args)
        } catch (e: IllegalArgumentException) {
            CliOutput.printError(e.message ?: "Invalid arguments")
            println()
            CliParser.printHelp()
            exitProcess(1)
        }

        // Show help and exit if requested
        if (config.showHelp) {
            CliParser.printHelp()
            exitProcess(0)
        }

        // Check for API key
        val apiKey = System.getenv("OPENROUTER_API_KEY")
        if (apiKey.isNullOrBlank()) {
            CliOutput.printError("OPENROUTER_API_KEY environment variable is not set.")
            println("Please set your OpenRouter API key:")
            println("  export OPENROUTER_API_KEY='your-api-key-here'")
            exitProcess(1)
        }

        // Run the interactive CLI
        runBlocking {
            startInteractiveCli(apiKey, config)
        }
    } catch (e: Exception) {
        println("Fatal error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

/**
 * Start the interactive CLI REPL loop.
 *
 * @param apiKey The OpenRouter API key
 * @param config The CLI configuration
 */
suspend fun startInteractiveCli(apiKey: String, config: llmchat.cli.CliConfig) {
    // Setup graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\nGoodbye!")
    })

    // Create the prompt executor
    val promptExecutor = simpleOpenRouterExecutor(apiKey)

    // Create conversation manager with agent factory
    val conversationManager = ConversationManager { systemPrompt ->
        AIAgent(
            promptExecutor = promptExecutor,
            systemPrompt = systemPrompt,
            llmModel = config.model.openRouterModel,
            temperature = config.temperature
        )
    }
    
    // Set the base system prompt
    conversationManager.setBaseSystemPrompt(config.systemPrompt)

    // Offer to restore previous session if one exists
    if (ConversationStorage.hasHistory()) {
        val count = ConversationStorage.size()
        print("Found previous conversation ($count messages). Resume? [y/N]: ")
        val answer = readlnOrNull()?.trim()?.lowercase()
        if (answer == "y" || answer == "yes") {
            conversationManager.loadHistory(ConversationStorage.load())
            CliOutput.printInfo("Loaded $count messages from previous session.")
        } else {
            ConversationStorage.clear()
        }
    }

    // Print welcome message
    CliOutput.printWelcome(config)

    // Main REPL loop
    while (true) {
        print("\nYou: ")
        val input = readMultilineInput() ?: break

        // Handle empty input
        if (input.isEmpty()) {
            continue
        }

        // Parse and execute command
        when (val command = Command.parse(input)) {
            is Command.Exit -> {
                CliOutput.printGoodbye()
                break
            }
            is Command.Help -> {
                CliOutput.printInteractiveHelp()
            }
            is Command.Clear -> {
                conversationManager.clearHistory()
                CliOutput.printInfo("Conversation history cleared.")
            }
            is Command.History -> {
                conversationManager.displayHistory()
            }
            is Command.Unknown -> {
                CliOutput.printError("Unknown command: ${command.input}")
                println("Type /help for available commands.")
            }
            is Command.Message -> {
                handleMessage(conversationManager, command.content)
            }
        }
    }
}

/**
 * Handle sending a message to the LLM.
 *
 * @param conversationManager The conversation manager
 * @param message The user's message
 */
suspend fun handleMessage(conversationManager: ConversationManager, message: String) {
    try {
        CliOutput.printThinkingIndicator()

        val result = conversationManager.sendMessage(message)

        result.fold(
            onSuccess = { stats ->
                // Clear "Thinking..." and print response
                CliOutput.clearThinkingIndicator()
                println(stats.response)
            },
            onFailure = { error ->
                println() // Clear thinking indicator
                CliOutput.printError("Error communicating with LLM: ${error.message}")
                println("Please try again or type /exit to quit.")
            }
        )
    } catch (e: Exception) {
        println() // Clear thinking indicator
        CliOutput.printError("Unexpected error: ${e.message}")
        println("Please try again or type /exit to quit.")
    }
}

/**
 * Read multiline input from the user.
 *
 * - For single line input: just press Enter
 * - For multiline input: press Enter twice (empty line signals end of input)
 * - Returns null if EOF is reached
 *
 * @return The user's input, or null if EOF
 */
fun readMultilineInput(): String? {
    val lines = mutableListOf<String>()

    while (true) {
        val line = readlnOrNull() ?: return if (lines.isEmpty()) null else lines.joinToString("\n")

        // If we get an empty line and we already have content, that's the end signal
        if (line.trim().isEmpty() && lines.isNotEmpty()) {
            break
        }

        // Skip leading empty lines
        if (line.trim().isEmpty() && lines.isEmpty()) {
            continue
        }

        lines.add(line)
    }

    return lines.joinToString("\n").trim()
}
