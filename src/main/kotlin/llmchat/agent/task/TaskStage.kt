package llmchat.agent.task

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStage(val displayName: String) {
    PLANNING("Planning"),
    EXECUTION("Execution"),
    VALIDATION("Validation"),
    DONE("Done"),
    ERROR("Error")
}
