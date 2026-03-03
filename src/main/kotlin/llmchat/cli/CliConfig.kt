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
    val profilePath: String? = null
)
