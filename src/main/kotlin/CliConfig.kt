data class CliConfig(
    val systemPrompt: String = "You are a helpful assistant. Answer user questions concisely.",
    val temperature: Double = 1.0,
    val showHelp: Boolean = false
)

object CliParser {
    fun parse(args: Array<String>): CliConfig {
        var systemPrompt = "You are a helpful assistant. Answer user questions concisely."
        var temperature = 1.0
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
                "--temperature" -> {
                    if (i + 1 < args.size) {
                        try {
                            temperature = args[i + 1].toDouble()
                            if (temperature !in 0.0..2.0) {
                                throw IllegalArgumentException("--temperature must be between 0.0 and 2.0")
                            }
                        } catch (e: NumberFormatException) {
                            throw IllegalArgumentException("--temperature must be a valid number")
                        }
                        i += 2
                    } else {
                        throw IllegalArgumentException("--temperature requires an argument")
                    }
                }
                else -> {
                    throw IllegalArgumentException("Unknown argument: ${args[i]}")
                }
            }
        }

        return CliConfig(systemPrompt, temperature, showHelp)
    }

    fun printHelp() {
        println(
            """
            === LLM Chat CLI ===
            
            Usage: ./gradlew run [--args="OPTIONS"]
            
            Options:
              --help, -h                Show this help message
              --system-prompt "TEXT"    Custom system prompt for the assistant
              --temperature VALUE       Sampling temperature (0.0-2.0, default: 1.0)
                                        Lower = more deterministic, Higher = more random
            
            Interactive Commands:
              /help       Show available commands
              /clear      Clear conversation history
              /history    Show conversation history
              /exit       Exit the application
              /quit       Exit the application
            
            Examples:
              ./gradlew run
              ./gradlew run --args="--system-prompt 'You are a coding assistant'"
              ./gradlew run --args="--temperature 0.7"
              ./gradlew run --args="--system-prompt 'You are helpful' --temperature 0.5"
            
            Environment Variables:
              OPENROUTER_API_KEY    Required: Your OpenRouter API key
            """.trimIndent()
        )
    }
}
