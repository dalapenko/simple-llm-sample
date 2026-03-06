package llmchat.agent.task

/**
 * A structured stage-transition proposal emitted by the LLM when operating
 * in autonomous mode. The LLM appends this to its response using the marker:
 *
 * ```
 * [TASK_TRANSITION]
 * to: <STAGE_NAME>
 * step: <what was accomplished>
 * reason: <why this stage is complete>
 * [/TASK_TRANSITION]
 * ```
 *
 * The proposal is extracted by [ConversationManager], stripped from the
 * visible response, and returned to the caller for approval/execution.
 */
data class TaskTransitionProposal(
    val targetStage: TaskStage,
    val step: String,
    val reason: String,
)
