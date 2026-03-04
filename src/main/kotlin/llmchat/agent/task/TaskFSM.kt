package llmchat.agent.task

import java.util.UUID

/**
 * Finite State Machine for tracking long-running task lifecycles.
 *
 * Valid transitions:
 *   PLANNING   → EXECUTION, ERROR
 *   EXECUTION  → VALIDATION, PLANNING, ERROR
 *   VALIDATION → DONE, PLANNING, EXECUTION, ERROR
 *   DONE       → (terminal)
 *   ERROR      → PLANNING
 */
class TaskFSM(private var state: TaskState) {

    private val validTransitions: Map<TaskStage, Set<TaskStage>> = mapOf(
        TaskStage.PLANNING   to setOf(TaskStage.EXECUTION, TaskStage.ERROR),
        TaskStage.EXECUTION  to setOf(TaskStage.VALIDATION, TaskStage.PLANNING, TaskStage.ERROR),
        TaskStage.VALIDATION to setOf(TaskStage.DONE, TaskStage.PLANNING, TaskStage.EXECUTION, TaskStage.ERROR),
        TaskStage.DONE       to emptySet(),
        TaskStage.ERROR      to setOf(TaskStage.PLANNING)
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

    fun buildResumptionContext(): String {
        val s = state
        return """
[ACTIVE TASK]
Task: ${s.taskDescription}
Stage: ${s.stage.name} (${s.stage.displayName})
Current Step: ${s.currentStep}
Expected Next Action: ${s.expectedAction.name}

You are resuming this task exactly where it left off. Do not ask the user to recap.
Continue from the current step. The conversation history above provides context.
[END ACTIVE TASK]""".trimIndent()
    }

    companion object {
        fun create(description: String): TaskFSM {
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
            return TaskFSM(state)
        }
    }
}
