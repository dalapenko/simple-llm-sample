data class CliConfig(
    val systemPrompt: String = "You are a helpful assistant. Answer user questions concisely.",
    val temperature: Double = 1.0,
    val model: String = "gpt-4o-mini",
    val showHelp: Boolean = false
)

object CliParser {
    private val AVAILABLE_MODELS = mapOf(
        "gpt-4o-mini" to "GPT4oMini",
        "mistral-7b" to "Mistral7B",
        "qwen-2.5" to "Qwen2_5",
        "gpt-4o" to "GPT4o"
    )

    fun parse(args: Array<String>): CliConfig {
        var systemPrompt = "You are a helpful assistant. Answer user questions concisely."
        var temperature = 1.0
        var model = "gpt-4o-mini"
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
                "--model" -> {
                    if (i + 1 < args.size) {
                        val requestedModel = args[i + 1]
                        if (!AVAILABLE_MODELS.containsKey(requestedModel)) {
                            throw IllegalArgumentException("--model must be one of: ${AVAILABLE_MODELS.keys.joinToString(", ")}")
                        }
                        model = requestedModel
                        i += 2
                    } else {
                        throw IllegalArgumentException("--model requires an argument")
                    }
                }
                else -> {
                    throw IllegalArgumentException("Unknown argument: ${args[i]}")
                }
            }
        }

        return CliConfig(systemPrompt, temperature, model, showHelp)
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
              --model MODEL             AI model to use (default: gpt-4o-mini)
                                        Available models:
                                          gpt-4o-mini  - GPT-4o Mini (default)
                                          mistral-7b   - Mistral 7B Instruct
                                          qwen-2.5     - Qwen 2.5 72B Instruct
                                          gpt-4o       - GPT-4o
            
            Interactive Commands:
              /help       Show available commands
              /clear      Clear conversation history
              /history    Show conversation history
              /exit       Exit the application
              /quit       Exit the application
            
            Examples:
              ./gradlew run
              ./gradlew run --args="--model gpt-4o"
              ./gradlew run --args="--system-prompt 'You are a coding assistant'"
              ./gradlew run --args="--temperature 0.7"
              ./gradlew run --args="--model mistral-7b --temperature 0.5"
            
            Environment Variables:
              OPENROUTER_API_KEY    Required: Your OpenRouter API key
            """.trimIndent()
        )
    }
}
