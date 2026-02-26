package llmchat.cli

import llmchat.agent.ContextWindowConfig
import llmchat.model.SupportedModel

/**
 * Parser for command-line arguments.
 */
object CliParser {
    /**
     * Parse command-line arguments into a configuration.
     *
     * @param args The command-line arguments
     * @return The parsed configuration
     * @throws IllegalArgumentException if arguments are invalid
     */
    fun parse(args: Array<String>): CliConfig {
        var systemPrompt = "You are a helpful assistant. Answer user questions concisely."
        var temperature = 1.0
        var model = SupportedModel.default
        var contextWindowSize = 10
        var summaryBatchSize = 10
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
                        model = SupportedModel.fromCliName(requestedModel)
                            ?: throw IllegalArgumentException(
                                "--model must be one of: ${SupportedModel.availableNames.joinToString(", ")}"
                            )
                        i += 2
                    } else {
                        throw IllegalArgumentException("--model requires an argument")
                    }
                }
                "--context-window" -> {
                    if (i + 1 < args.size) {
                        val n = args[i + 1].toIntOrNull()
                            ?: throw IllegalArgumentException("--context-window must be a positive integer")
                        if (n < 1) throw IllegalArgumentException("--context-window must be >= 1")
                        contextWindowSize = n
                        i += 2
                    } else {
                        throw IllegalArgumentException("--context-window requires an argument")
                    }
                }
                "--summary-batch" -> {
                    if (i + 1 < args.size) {
                        val n = args[i + 1].toIntOrNull()
                            ?: throw IllegalArgumentException("--summary-batch must be a positive integer")
                        if (n < 1) throw IllegalArgumentException("--summary-batch must be >= 1")
                        summaryBatchSize = n
                        i += 2
                    } else {
                        throw IllegalArgumentException("--summary-batch requires an argument")
                    }
                }
                else -> {
                    throw IllegalArgumentException("Unknown argument: ${args[i]}")
                }
            }
        }

        return CliConfig(systemPrompt, temperature, model, ContextWindowConfig(contextWindowSize, summaryBatchSize), showHelp)
    }

    /**
     * Print help text to the console.
     */
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
              --context-window N        Keep last N messages verbatim (default: 10)
              --summary-batch N         Summarize old messages in batches of N (default: 10)
              --model MODEL             AI model to use (default: ${SupportedModel.default.cliName})
                                        Available models:
${SupportedModel.entries.joinToString("\n") { "                                          ${it.cliName.padEnd(12)} - ${it.displayName}" }}
            
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
