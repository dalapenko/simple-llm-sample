package mcpscheduler

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY }

@Serializable
data class Reminder(
    val id: String,
    val title: String,
    val description: String = "",
    val scheduledAt: Long,
    val recurrence: Recurrence = Recurrence.NONE,
    val recurrenceIntervalDays: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val isDismissed: Boolean = false,
    val lastTriggeredAt: Long? = null
) {
    companion object {
        fun generateId(): String =
            "REM-" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
    }
}

/** Records one auto-execution by the background scheduler coroutine. */
data class TriggerEvent(
    val reminderId: String,
    val title: String,
    val triggeredAt: Long,       // epoch millis when the scheduler fired
    val nextScheduledAt: Long    // new scheduledAt after auto-advance
)
