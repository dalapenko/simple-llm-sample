data class CliConfig(
    val systemPrompt: String = "You are a helpful assistant. Answer user questions concisely.",
    val showHelp: Boolean = false
)

object CliParser {
    fun parse(args: Array<String>): CliConfig {
        var systemPrompt = "You are a helpful assistant. Answer user questions concisely."
        var showHelp = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--help", "-h" -> {
                    showHelp = true
                    i++
                }
                "--system-prompt" -> {
                    if (i + 1 < args.size) {
                        systemPrompt = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("--system-prompt requires an argument")
                    }
                }
                else -> {
                    throw IllegalArgumentException("Unknown argument: ${args[i]}")
                }
            }
        }

        return CliConfig(systemPrompt, showHelp)
    }

    fun printHelp() {
        println(
            """
            === LLM Chat CLI ===
            
            Usage: ./gradlew run [--args="OPTIONS"]
            
            Options:
              --help, -h                Show this help message
              --system-prompt "TEXT"    Custom system prompt for the assistant
            
            Interactive Commands:
              /help       Show available commands
              /clear      Clear conversation history
              /history    Show conversation history
              /exit       Exit the application
              /quit       Exit the application
            
            Examples:
              ./gradlew run
              ./gradlew run --args="--system-prompt 'You are a coding assistant'"
            
            Environment Variables:
              OPENROUTER_API_KEY    Required: Your OpenRouter API key
            """.trimIndent()
        )
    }
}
