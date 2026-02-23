package llmchat.cli

/**
 * Represents commands that can be executed in the interactive CLI.
 *
 * Commands are parsed from user input and dispatched to appropriate handlers.
 */
sealed class Command {
    /**
     * Exit the application.
     */
    data object Exit : Command()

    /**
     * Display help information.
     */
    data object Help : Command()

    /**
     * Clear conversation history.
     */
    data object Clear : Command()

    /**
     * Display conversation history.
     */
    data object History : Command()

    /**
     * Send a message to the LLM.
     *
     * @property content The message content
     */
    data class Message(val content: String) : Command()

    /**
     * Unknown command that wasn't recognized.
     *
     * @property input The original input string
     */
    data class Unknown(val input: String) : Command()

    companion object {
        /**
         * Parse user input into a command.
         *
         * @param input The raw user input
         * @return The parsed command
         */
        fun parse(input: String): Command = when {
            input == "/exit" || input == "/quit" -> Exit
            input == "/help" -> Help
            input == "/clear" -> Clear
            input == "/history" -> History
            input.startsWith("/") -> Unknown(input)
            else -> Message(input)
        }
    }
}
