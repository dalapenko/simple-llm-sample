package llmchat.agent.task

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStage(val displayName: String) {
    PLANNING("Planning"),
    PLAN_APPROVED("Plan Approved"),
    EXECUTION("Execution"),
    VALIDATION("Validation"),
    DONE("Done"),
    ERROR("Error")
}
