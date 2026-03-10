package llmchat.agent.task

import kotlinx.serialization.Serializable

@Serializable
data class TaskTransitionRecord(
    val fromStage: TaskStage,
    val toStage: TaskStage,
    val step: String,
    val timestamp: Long
)

@Serializable
data class TaskState(
    val id: String,
    val taskDescription: String,
    val stage: TaskStage,
    val currentStep: String,
    val expectedAction: ExpectedAction,
    val createdAt: Long,
    val updatedAt: Long,
    val history: List<TaskTransitionRecord>
)
