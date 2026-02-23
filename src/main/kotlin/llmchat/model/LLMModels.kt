package llmchat.model

import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLModel

/**
 * Supported LLM models for the CLI application.
 *
 * This enum provides a single source of truth for all model-related information,
 * including CLI names, display names, and OpenRouter model references.
 *
 * @property cliName The name used in command-line arguments
 * @property displayName The human-readable name shown to users
 * @property openRouterModel The OpenRouter model reference
 */
enum class SupportedModel(
    val cliName: String,
    val displayName: String,
    val openRouterModel: LLModel
) {
    GPT4O_MINI("gpt-4o-mini", "GPT-4o Mini", OpenRouterModels.GPT4oMini),
    MISTRAL_7B("mistral-7b", "Mistral 7B Instruct", OpenRouterModels.Mistral7B),
    QWEN_2_5("qwen-2.5", "Qwen 2.5 72B Instruct", OpenRouterModels.Qwen2_5),
    GPT4O("gpt-4o", "GPT-4o", OpenRouterModels.GPT4o);

    companion object {
        /**
         * Find a model by its CLI name.
         *
         * @param name The CLI name to search for
         * @return The matching model, or null if not found
         */
        fun fromCliName(name: String): SupportedModel? =
            entries.find { it.cliName == name }

        /**
         * Get a list of all available CLI model names.
         */
        val availableNames: List<String> = entries.map { it.cliName }

        /**
         * Default model used when none is specified.
         */
        val default: SupportedModel = GPT4O_MINI
    }
}
