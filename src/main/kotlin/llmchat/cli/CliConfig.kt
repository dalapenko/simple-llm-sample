package llmchat.cli

import llmchat.model.SupportedModel

/**
 * CLI configuration for the LLM Chat application.
 *
 * @property systemPrompt The system prompt to use for the AI assistant
 * @property temperature The sampling temperature for LLM responses (0.0-2.0)
 * @property model The LLM model to use
 * @property showHelp Whether to show help and exit
 */
data class CliConfig(
    val systemPrompt: String = "You are a helpful assistant. Answer user questions concisely.",
    val temperature: Double = 1.0,
    val model: SupportedModel = SupportedModel.default,
    val showHelp: Boolean = false
)
