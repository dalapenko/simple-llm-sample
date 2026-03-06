package llmchat.agent.task

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStage(val displayName: String) {
    PLANNING("Planning"),
    PLAN_APPROVED("Plan Approved"),
    EXECUTION("Execution"),
    VALIDATION("Validation"),
    DONE("Done"),
    ERROR("Error");

    /**
     * Approval policy for entering this stage in autonomous mode:
     * - [ExpectedAction.USER_APPROVAL] — the LLM may propose but a human must confirm
     * - [ExpectedAction.LLM_GENERATION] — the transition executes automatically
     */
    val requiredApproval: ExpectedAction get() = when (this) {
        PLANNING      -> ExpectedAction.LLM_GENERATION
        PLAN_APPROVED -> ExpectedAction.USER_APPROVAL
        EXECUTION     -> ExpectedAction.LLM_GENERATION
        VALIDATION    -> ExpectedAction.USER_APPROVAL
        DONE          -> ExpectedAction.USER_APPROVAL
        ERROR         -> ExpectedAction.LLM_GENERATION
    }
}
