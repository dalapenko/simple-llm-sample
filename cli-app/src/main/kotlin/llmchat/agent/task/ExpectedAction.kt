package llmchat.agent.task

import kotlinx.serialization.Serializable

@Serializable
enum class ExpectedAction(val displayName: String) {
    LLM_GENERATION("LLM Generation"),
    TOOL_EXECUTION("Tool Execution"),
    USER_APPROVAL("User Approval"),
    USER_INPUT("User Input")
}
