package llmchat.cli

import llmchat.agent.ContextWindowConfig
import llmchat.model.SupportedModel

data class CliConfig(
    val systemPrompt: String = "You are a helpful assistant. Answer user questions concisely.",
    val temperature: Double = 1.0,
    val model: SupportedModel = SupportedModel.default,
    val contextWindow: ContextWindowConfig = ContextWindowConfig(),
    val strategyType: StrategyType = StrategyType.default,
    val showHelp: Boolean = false,
    val profilePath: String? = null,
    val ragMode: RagMode? = null,
    val similarityThreshold: Double = 0.65,
    val ragTopK: Int = 5,
    val provider: LlmProvider = LlmProvider.default,
    val localModelName: String = "llama3.2",
    val localUrl: String = "http://localhost:11434",
    val localEmbeddingModel: String = "nomic-embed-text",
    /** Maximum tokens to generate per response. Maps to Ollama's num_predict. Null = model default. */
    val localMaxTokens: Int? = null,
    /** Context window size passed to Ollama (num_ctx) and used as LLModel.contextLength. Null = Ollama default. */
    val localContextLength: Int? = null,
    /** Name of the active preset, if loaded via --preset. Informational only. */
    val presetName: String? = null,
    /** Run in headless/CI mode: single-turn, no interactive terminal, plain stdout output. */
    val headlessMode: Boolean = false,
    /** Path to a file containing the git diff to review (headless mode). Falls back to stdin if null. */
    val diffFilePath: String? = null,
    /** Path to a directory to index before reviewing (headless mode). Skips if knowledge base already exists. */
    val buildIndexPath: String? = null,
) {
    val ragEnabled: Boolean get() = ragMode != null
}
