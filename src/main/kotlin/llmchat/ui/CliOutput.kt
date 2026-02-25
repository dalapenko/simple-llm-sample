package llmchat.ui

import llmchat.cli.CliConfig

/**
 * Handles all CLI output and formatting.
 *
 * This object centralizes all user-facing output, including welcome messages,
 * help text, and error messages.
 */
object CliOutput {
    /**
     * Print the welcome message with current configuration.
     *
     * @param config The CLI configuration to display
     */
    fun printWelcome(config: CliConfig) {
        println(
            """
        
        ╔═══════════════════════════════════════╗
        ║         LLM Chat CLI                  ║
        ║  Powered by Koog & OpenRouter         ║
        ╚═══════════════════════════════════════╝
        
        Model: ${config.model.displayName}
        Temperature: ${config.temperature}
        
        Type your message and press Enter twice to send.
        (First Enter = new line, Second Enter on empty line = send)
        Commands: /help, /clear, /history, /exit
        
        """.trimIndent()
        )
    }

    /**
     * Print the interactive help message.
     */
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

    /**
     * Print token usage statistics for the last request.
     */
    fun printTokenStats(inputTokens: Int, historyTokens: Int, responseTokens: Int, totalTokens: Int) {
        println("\n[Tokens] request: ~$inputTokens | history: ~$historyTokens | response: ~$responseTokens | total: ~$totalTokens")
    }

    /**
     * Print a thinking indicator (before receiving response).
     */
    fun printThinkingIndicator() {
        print("Assistant: Thinking...")
    }

    /**
     * Clear the thinking indicator and prepare for response.
     */
    fun clearThinkingIndicator() {
        print("\r")
        print("Assistant: ")
    }

    /**
     * Print an error message.
     *
     * @param message The error message to display
     */
    fun printError(message: String) {
        println("Error: $message")
    }

    /**
     * Print a general informational message.
     *
     * @param message The message to display
     */
    fun printInfo(message: String) {
        println(message)
    }

    /**
     * Print the goodbye message.
     */
    fun printGoodbye() {
        println("Goodbye!")
    }
}
