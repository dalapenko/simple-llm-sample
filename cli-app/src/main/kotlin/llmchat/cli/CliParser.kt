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
        var provider = LlmProvider.default
        var localModelName = "llama3.2"
        var localUrl = "http://localhost:11434"
        var localEmbeddingModel = "nomic-embed-text"
        var localMaxTokens: Int? = null
        var localContextLength: Int? = null
        var presetName: String? = null
        var headlessMode = false
        var diffFilePath: String? = null
        var buildIndexPath: String? = null

        // Track fields explicitly set by the user so preset values don't overwrite them.
        val explicitlySet = mutableSetOf<String>()

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
                        explicitlySet += "systemPrompt"
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
                        explicitlySet += "temperature"
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
                        explicitlySet += "contextWindow"
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

                "--provider" -> {
                    if (i + 1 < args.size) {
                        val name = args[i + 1]
                        provider = LlmProvider.fromCliName(name)
                            ?: throw IllegalArgumentException(
                                "--provider must be one of: ${LlmProvider.availableNames.joinToString(", ")}"
                            )
                        i += 2
                    } else {
                        throw IllegalArgumentException("--provider requires an argument")
                    }
                }

                "--local-model" -> {
                    if (i + 1 < args.size) {
                        localModelName = args[i + 1]
                        explicitlySet += "localModel"
                        i += 2
                    } else {
                        throw IllegalArgumentException("--local-model requires an argument")
                    }
                }

                "--local-url" -> {
                    if (i + 1 < args.size) {
                        localUrl = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("--local-url requires an argument")
                    }
                }

                "--embedding-model" -> {
                    if (i + 1 < args.size) {
                        localEmbeddingModel = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("--embedding-model requires an argument")
                    }
                }

                "--preset" -> {
                    if (i + 1 < args.size) {
                        presetName = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("--preset requires a name argument")
                    }
                }

                "--local-max-tokens" -> {
                    if (i + 1 < args.size) {
                        val n = args[i + 1].toIntOrNull()
                            ?: throw IllegalArgumentException("--local-max-tokens must be a positive integer")
                        if (n < 1) throw IllegalArgumentException("--local-max-tokens must be >= 1")
                        localMaxTokens = n
                        explicitlySet += "localMaxTokens"
                        i += 2
                    } else {
                        throw IllegalArgumentException("--local-max-tokens requires an argument")
                    }
                }

                "--local-context-length" -> {
                    if (i + 1 < args.size) {
                        val n = args[i + 1].toIntOrNull()
                            ?: throw IllegalArgumentException("--local-context-length must be a positive integer")
                        if (n < 1) throw IllegalArgumentException("--local-context-length must be >= 1")
                        localContextLength = n
                        explicitlySet += "localContextLength"
                        i += 2
                    } else {
                        throw IllegalArgumentException("--local-context-length requires an argument")
                    }
                }

                "--headless" -> {
                    headlessMode = true
                    i++
                }

                "--diff-file" -> {
                    if (i + 1 < args.size) {
                        diffFilePath = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("--diff-file requires a path argument")
                    }
                }

                "--build-index" -> {
                    if (i + 1 < args.size) {
                        buildIndexPath = args[i + 1]
                        i += 2
                    } else {
                        throw IllegalArgumentException("--build-index requires a directory path argument")
                    }
                }

                else -> {
                    throw IllegalArgumentException("Unknown argument: ${args[i]}")
                }
            }
        }

        // Apply preset: fill in any field the user did not explicitly set.
        val preset = presetName?.let { name ->
            PresetLoader.load(name)
                ?: throw IllegalArgumentException(
                    "Unknown preset: '$name'. Available: ${PresetLoader.availableNames.joinToString(", ")}" +
                            "\nCustom presets can be placed in ./presets/<name>.json or ~/.llmchat/presets/<name>.json"
                )
        }

        if ("systemPrompt" !in explicitlySet) preset?.systemPrompt?.let { systemPrompt = it }
        if ("temperature" !in explicitlySet) preset?.temperature?.let { temperature = it }
        if ("contextWindow" !in explicitlySet) preset?.contextWindow?.let { contextWindowSize = it }
        if ("localModel" !in explicitlySet) preset?.localModel?.let { localModelName = it }
        if ("localMaxTokens" !in explicitlySet) preset?.maxTokens?.let { localMaxTokens = it }
        if ("localContextLength" !in explicitlySet) preset?.contextLength?.let { localContextLength = it }

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
            ragTopK,
            provider,
            localModelName,
            localUrl,
            localEmbeddingModel,
            localMaxTokens,
            localContextLength,
            presetName,
            headlessMode,
            diffFilePath,
            buildIndexPath,
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
              --provider PROVIDER       LLM provider (default: ${LlmProvider.default.cliName})
                                        Available: ${LlmProvider.availableNames.joinToString(", ")}
              --local-model NAME        Model name for Ollama (default: llama3.2)
                                        Examples: llama3.2, mistral, qwen2.5:7b, phi4
                                        Quantization via tag: qwen2.5-coder:7b-q4_K_M, llama3.2:3b-q8_0
              --local-url URL           Ollama server URL (default: http://localhost:11434)
              --local-max-tokens N      Max tokens to generate per response (Ollama: num_predict)
                                        Limits output length. Does not affect the input context window.
              --local-context-length N  Context window size sent to Ollama (num_ctx, default: Ollama default)
                                        Also sets the model's token budget for Koog's sliding window.
                                        Requires enough VRAM/RAM for the requested size.
              --preset NAME             Apply a named configuration preset (overridable by other flags)
                                        Built-in: ${PresetLoader.availableNames.joinToString(", ")}
                                        Custom: ./presets/<name>.json or ~/.llmchat/presets/<name>.json
              --embedding-model NAME    Ollama embedding model for /index and RAG (default: nomic-embed-text)
                                        Examples: nomic-embed-text (768d), mxbai-embed-large (1024d), all-minilm (384d)
                                        Note: index and RAG must use the same embedding model.

            Headless / CI Mode:
              --headless                Run in headless CI mode: single LLM turn, plain stdout, then exit.
                                        Designed for GitHub Actions and other non-interactive pipelines.
              --diff-file PATH          Path to a file containing the git diff to review (headless only).
                                        If omitted, diff is read from stdin.
              --build-index PATH        Index a directory before running the review (headless only).
                                        Skips indexing if the knowledge base already exists.

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
            
            Examples (Ollama / local):
              ./gradlew run --args="--provider ollama"
              ./gradlew run --args="--provider ollama --local-model mistral"
              ./gradlew run --args="--provider ollama --local-model qwen2.5:7b --local-url http://192.168.1.10:11434"
              ./gradlew run --args="--provider ollama --local-max-tokens 2048 --local-context-length 16384"

            Examples (presets — for A/B comparison):
              ./gradlew run --args="--provider ollama --preset baseline"
              ./gradlew run --args="--provider ollama --preset code-review"
              ./gradlew run --args="--provider ollama --preset code-review --temperature 0.3"  (override one field)

            Environment Variables:
              OPENROUTER_API_KEY    Required for --provider openrouter (default). Not needed for local providers.
            """.trimIndent()
        )
    }
}
