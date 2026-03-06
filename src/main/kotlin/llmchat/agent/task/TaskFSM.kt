package llmchat.agent.task

import java.util.UUID

/**
 * Finite State Machine for tracking long-running task lifecycles.
 *
 * Valid transitions:
 *   PLANNING      → PLAN_APPROVED, ERROR
 *   PLAN_APPROVED → EXECUTION, PLANNING, ERROR
 *   EXECUTION     → VALIDATION, PLANNING, ERROR
 *   VALIDATION    → DONE, PLANNING, EXECUTION, ERROR
 *   DONE          → (terminal)
 *   ERROR         → PLANNING
 *
 * PLAN_APPROVED acts as a mandatory chokepoint: execution cannot begin until
 * the plan has been explicitly approved, preventing the agent from skipping
 * the planning phase.
 *
 * Optional [guards] are evaluated after the structural graph check. Each guard
 * may [GuardResult.Deny] a transition with a reason (and optional invariant ID),
 * or [GuardResult.Allow] it to proceed.
 */
class TaskFSM(
    private var state: TaskState,
    private val guards: List<TransitionGuard> = emptyList()
) {

    private val validTransitions: Map<TaskStage, Set<TaskStage>> = mapOf(
        TaskStage.PLANNING      to setOf(TaskStage.PLAN_APPROVED, TaskStage.ERROR),
        TaskStage.PLAN_APPROVED to setOf(TaskStage.EXECUTION, TaskStage.PLANNING, TaskStage.ERROR),
        TaskStage.EXECUTION     to setOf(TaskStage.VALIDATION, TaskStage.PLANNING, TaskStage.ERROR),
        TaskStage.VALIDATION    to setOf(TaskStage.DONE, TaskStage.PLANNING, TaskStage.EXECUTION, TaskStage.ERROR),
        TaskStage.DONE          to emptySet(),
        TaskStage.ERROR         to setOf(TaskStage.PLANNING)
    )

    fun getState(): TaskState = state

    fun transition(
        newStage: TaskStage,
        newStep: String = "",
        expectedAction: ExpectedAction = ExpectedAction.LLM_GENERATION
    ): Result<TaskState> {
        val allowed = validTransitions[state.stage] ?: emptySet()
        if (newStage !in allowed) {
            return Result.failure(
                IllegalStateException(
                    "Invalid transition: ${state.stage} → $newStage. " +
                    "Allowed from ${state.stage}: ${allowed.joinToString { it.name }}"
                )
            )
        }

        for (guard in guards) {
            val result = guard.validate(state.stage, newStage)
            if (result is GuardResult.Deny) {
                val invariantSuffix = result.invariantId?.let { " [Invariant: $it]" }.orEmpty()
                return Result.failure(
                    IllegalStateException(
                        "Transition ${state.stage} → $newStage blocked: ${result.reason}$invariantSuffix"
                    )
                )
            }
        }

        val now = System.currentTimeMillis()
        val record = TaskTransitionRecord(
            fromStage = state.stage,
            toStage = newStage,
            step = newStep,
            timestamp = now
        )

        state = state.copy(
            stage = newStage,
            currentStep = newStep,
            expectedAction = expectedAction,
            updatedAt = now,
            history = state.history + record
        )

        return Result.success(state)
    }

    fun updateStep(step: String, expectedAction: ExpectedAction = ExpectedAction.LLM_GENERATION): TaskState {
        state = state.copy(
            currentStep = step,
            expectedAction = expectedAction,
            updatedAt = System.currentTimeMillis()
        )
        return state
    }

    fun buildResumptionContext(autoMode: Boolean = false): String {
        val s = state
        val allowed = validTransitions[s.stage] ?: emptySet()
        return buildString {
            appendLine("[ACTIVE TASK]")
            appendLine("Task: ${s.taskDescription}")
            appendLine("Stage: ${s.stage.name} (${s.stage.displayName})")
            appendLine("Current Step: ${s.currentStep}")
            append("Expected Next Action: ${s.expectedAction.name}")
            if (autoMode) {
                val approvalRequired = allowed
                    .filter { it.requiredApproval == ExpectedAction.USER_APPROVAL }
                    .joinToString(", ") { it.name }
                appendLine()
                appendLine()
                appendLine("You are operating in AUTONOMOUS mode.")
                appendLine("When you believe the current stage objectives are fully complete,")
                appendLine("append a transition proposal at the very END of your response:")
                appendLine()
                appendLine("[TASK_TRANSITION]")
                appendLine("to: <STAGE_NAME>")
                appendLine("step: <brief description of what was accomplished in this stage>")
                appendLine("reason: <why this stage is complete>")
                appendLine("[/TASK_TRANSITION]")
                appendLine()
                appendLine("Allowed next stages: ${allowed.joinToString(", ") { it.name }}")
                if (approvalRequired.isNotEmpty()) {
                    appendLine("Stages requiring user approval before execution: $approvalRequired")
                }
                append("Do NOT propose a transition unless the stage objectives are genuinely complete.")
            } else {
                appendLine()
                appendLine()
                appendLine("You are resuming this task exactly where it left off. Do not ask the user to recap.")
                append("Continue from the current step. The conversation history above provides context.")
            }
            appendLine()
            append("[END ACTIVE TASK]")
        }
    }

    companion object {
        fun create(description: String, guards: List<TransitionGuard> = emptyList()): TaskFSM {
            val now = System.currentTimeMillis()
            val state = TaskState(
                id = UUID.randomUUID().toString(),
                taskDescription = description,
                stage = TaskStage.PLANNING,
                currentStep = "Initial planning",
                expectedAction = ExpectedAction.LLM_GENERATION,
                createdAt = now,
                updatedAt = now,
                history = emptyList()
            )
            return TaskFSM(state, guards)
        }
    }
}
