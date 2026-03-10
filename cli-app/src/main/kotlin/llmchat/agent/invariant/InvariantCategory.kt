package llmchat.agent.invariant

import kotlinx.serialization.Serializable

@Serializable
enum class InvariantCategory(val displayName: String, val cliName: String) {
    STACK("Technology Stack", "stack"),
    ARCHITECTURE("Architecture", "architecture"),
    BUSINESS_RULE("Business Rule", "business-rule"),
    GENERAL("General", "general");

    companion object {
        fun fromCliName(name: String): InvariantCategory? =
            entries.find { it.cliName.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true) }
    }
}
