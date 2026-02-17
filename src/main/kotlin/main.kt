import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        // Parse command-line arguments
        val config = try {
            CliParser.parse(args)
        } catch (e: IllegalArgumentException) {
            println("Error: ${e.message}")
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
            println("Error: OPENROUTER_API_KEY environment variable is not set.")
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

suspend fun startInteractiveCli(apiKey: String, config: CliConfig) {
    // Setup graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\nGoodbye!")
    })

    // Create the prompt executor (reusable across agents)
    val promptExecutor = simpleOpenRouterExecutor(apiKey)

    // Create conversation manager with an agent factory
    // This creates a new agent instance for each message to avoid single-use constraint
    val conversationManager = ConversationManager {
        AIAgent(
            promptExecutor = promptExecutor,
            systemPrompt = config.systemPrompt,
            llmModel = OpenRouterModels.GPT4oMini
        )
    }

    // Print welcome message
    printWelcome()

    // Main REPL loop
    while (true) {
        print("\nYou: ")
        val input = readMultilineInput() ?: break

        // Handle empty input
        if (input.isEmpty()) {
            continue
        }

        // Handle commands
        when {
            input == "/exit" || input == "/quit" -> {
                println("Goodbye!")
                break
            }
            input == "/help" -> {
                printInteractiveHelp()
                continue
            }
            input == "/clear" -> {
                conversationManager.clearHistory()
                println("Conversation history cleared.")
                continue
            }
            input == "/history" -> {
                conversationManager.displayHistory()
                continue
            }
            input.startsWith("/") -> {
                println("Unknown command: $input")
                println("Type /help for available commands.")
                continue
            }
        }

        // Send message to agent with visual feedback
        try {
            print("Assistant: ")
            print("Thinking...")
            
            val response = conversationManager.sendMessage(input)
            
            // Clear "Thinking..." and print response
            print("\r")
            print("Assistant: ")
            println(response)
        } catch (e: Exception) {
            println("\nError communicating with LLM: ${e.message}")
            println("Please try again or type /exit to quit.")
        }
    }
}

/**
 * Reads multiline input from the user.
 * - For single line input: just press Enter
 * - For multiline input: press Enter twice (empty line signals end of input)
 * - Returns null if EOF is reached
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

fun printWelcome() {
    println(
        """
        
        ╔═══════════════════════════════════════╗
        ║         LLM Chat CLI                  ║
        ║  Powered by Koog & OpenRouter         ║
        ╚═══════════════════════════════════════╝
        
        Type your message and press Enter twice to send.
        (First Enter = new line, Second Enter on empty line = send)
        Commands: /help, /clear, /history, /exit
        
        """.trimIndent()
    )
}

fun printInteractiveHelp() {
    println(
        """
        
        Available Commands:
          /help       Show this help message
          /clear      Clear conversation history
          /history    Show conversation history
          /exit       Exit the application
          /quit       Exit the application
        
        How to use:
          - Type your message (can be multiple lines)
          - Press Enter twice (empty line) to send
          - For single line, just press Enter twice
        """.trimIndent()
    )
}