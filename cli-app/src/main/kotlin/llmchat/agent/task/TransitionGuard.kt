package llmchat.agent.task

/**
 * Result type returned by a [TransitionGuard].
 *
 * [Allow] permits the transition to proceed.
 * [Deny] blocks the transition with a human-readable [reason] and an optional
 * [invariantId] reference (e.g. "INV-A1B2C3") so the error message can cite
 * which project invariant was violated.
 */
sealed class GuardResult {
    data object Allow : GuardResult()
    data class Deny(val reason: String, val invariantId: String? = null) : GuardResult()
}

/**
 * Pluggable precondition hook evaluated during every [TaskFSM.transition] call,
 * after the structural graph check passes.
 *
 * Implement this interface to inject business-rule or invariant-based checks
 * without coupling [TaskFSM] to any specific storage layer.
 *
 * Example — blocking PLAN_APPROVED if no plan document exists:
 * ```kotlin
 * val guard = TransitionGuard { from, to ->
 *     if (to == TaskStage.PLAN_APPROVED && !planDocument.exists()) {
 *         GuardResult.Deny("Plan document must be created before approval", invariantId = "INV-A1B2C3")
 *     } else {
 *         GuardResult.Allow
 *     }
 * }
 * val fsm = TaskFSM.create("my task", guards = listOf(guard))
 * ```
 */
fun interface TransitionGuard {
    fun validate(from: TaskStage, to: TaskStage): GuardResult
}
