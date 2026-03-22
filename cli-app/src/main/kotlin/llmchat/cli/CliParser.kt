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
        var strategyType = StrategyType.default
        var showHelp = false
        var profilePath: String? = null
        var ragMode: RagMode? = null
        var similarityThreshold = 0.65
        var ragTopK = 5

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
                        } catch (_: NumberFormatException) {
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

                "--strategy" -> {
                    if (i + 1 < args.size) {
                        val name = args[i + 1]
                        strategyType = StrategyType.fromCliName(name)
                            ?: throw IllegalArgumentException(
                                "--strategy must be one of: ${StrategyType.availableNames.joinToString(", ")}"
                            )
                        i += 2
                    } else {
                        throw IllegalArgumentException("--strategy requires an argument")
                    }
                }

                "--profile" -> {
                    if (i + 1 < args.size) {
                        profilePath = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("--profile requires a path argument")
                    }
                }

                "--rag" -> {
                    if (ragMode == null) ragMode = RagMode.BASIC
                    i++
                }

                "--no-rag" -> {
                    ragMode = null
                    i++
                }

                "--mode" -> {
                    if (i + 1 < args.size) {
                        val name = args[i + 1]
                        ragMode = RagMode.fromCliName(name)
                            ?: throw IllegalArgumentException(
                                "--mode must be one of: ${RagMode.availableNames.joinToString(", ")}"
                            )
                        i += 2
                    } else {
                        throw IllegalArgumentException("--mode requires an argument")
                    }
                }

                "--threshold" -> {
                    if (i + 1 < args.size) {
                        val v = args[i + 1].toDoubleOrNull()
                            ?: throw IllegalArgumentException("--threshold must be a number between 0.0 and 1.0")
                        if (v !in 0.0..1.0) throw IllegalArgumentException("--threshold must be between 0.0 and 1.0")
                        similarityThreshold = v
                        i += 2
                    } else {
                        throw IllegalArgumentException("--threshold requires an argument")
                    }
                }

                "--top-k" -> {
                    if (i + 1 < args.size) {
                        val n = args[i + 1].toIntOrNull()
                            ?: throw IllegalArgumentException("--top-k must be a positive integer")
                        if (n < 1) throw IllegalArgumentException("--top-k must be >= 1")
                        ragTopK = n
                        i += 2
                    } else {
                        throw IllegalArgumentException("--top-k requires an argument")
                    }
                }

                else -> {
                    throw IllegalArgumentException("Unknown argument: ${args[i]}")
                }
            }
        }

        return CliConfig(
            systemPrompt,
            temperature,
            model,
            ContextWindowConfig(contextWindowSize, summaryBatchSize),
            strategyType,
            showHelp,
            profilePath,
            ragMode,
            similarityThreshold,
            ragTopK
        )
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
              --context-window N        Keep last N messages in the window (default: 10)
              --summary-batch N         (legacy, only used without --strategy)
              --strategy STRATEGY       Context strategy (default: ${StrategyType.default.cliName})
                                        Available strategies:
${StrategyType.entries.joinToString("\n") { "                                          ${it.cliName.padEnd(16)} - ${it.displayName}" }}
              --model MODEL             AI model to use (default: ${SupportedModel.default.cliName})
                                        Available models:
${SupportedModel.entries.joinToString("\n") { "                                          ${it.cliName.padEnd(12)} - ${it.displayName}" }}
              --profile PATH            Path to a profile.md file (default: ~/.llmchat/profile.md)
                                        Injected into every request as user preferences.
                                        See profiles/sample.md in the repo for an example.
              --mode MODE               RAG mode: basic, advanced, or conversational.
                                        basic: retrieve→generate
                                        advanced: rewrite→filter→rerank→generate
                                        conversational: history-aware + task state + advanced
                                        Requires: /index <path> run first.
                                        Available: ${RagMode.availableNames.joinToString(", ")}
              --rag                     Shorthand for --mode basic
              --no-rag                  Disable RAG (default)
              --threshold VALUE         Similarity threshold for advanced mode (0.0–1.0, default: 0.65)
                                        Chunks below this score are filtered before reranking.
              --top-k N                 Final number of chunks sent to LLM (default: 5 basic / 3 advanced)

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
              ./gradlew run --args="--profile profiles/sample.md"
            
            Environment Variables:
              OPENROUTER_API_KEY    Required: Your OpenRouter API key
            """.trimIndent()
        )
    }
}
