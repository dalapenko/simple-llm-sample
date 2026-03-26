package llmchat.cli

import kotlinx.serialization.Serializable

/**
 * A named configuration bundle for the local (Ollama) provider.
 *
 * Each non-null field overrides the corresponding CLI default when the preset is loaded via --preset.
 * Explicitly passed CLI flags always win over preset values.
 *
 * Fields:
 * - [temperature]     Controls output randomness (0.0 = deterministic, 2.0 = very random).
 * - [maxTokens]       Maximum tokens to generate per response (Ollama: num_predict).
 * - [contextLength]   Context window size passed to Ollama (num_ctx). Also sets LLModel.contextLength.
 * - [contextWindow]   Number of conversation messages kept in the sliding window.
 * - [localModel]      Ollama model name, including quantization tag (e.g. qwen2.5-coder:7b-q4_K_M).
 * - [systemPrompt]    Task-specific system prompt injected on every request.
 */
@Serializable
data class LocalProviderPreset(
    val name: String,
    val description: String = "",
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val contextLength: Int? = null,
    val contextWindow: Int? = null,
    val localModel: String? = null,
    val systemPrompt: String? = null,
)
