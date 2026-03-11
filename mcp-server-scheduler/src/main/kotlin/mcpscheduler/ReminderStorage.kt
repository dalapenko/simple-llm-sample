package mcpscheduler

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderStorage(
    private val storageFile: File = defaultStorageFile()
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val reminders = mutableListOf<Reminder>()

    init {
        load()
    }

    fun add(reminder: Reminder): Reminder {
        reminders.add(reminder)
        persist()
        return reminder
    }

    fun list(includeDismissed: Boolean = false): List<Reminder> =
        if (includeDismissed) reminders.toList()
        else reminders.filter { !it.isDismissed }

    fun getDue(): List<Reminder> {
        val now = System.currentTimeMillis()
        return reminders.filter { !it.isDismissed && it.scheduledAt <= now }
    }

    fun findById(id: String): Reminder? =
        reminders.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun cancel(id: String): Boolean {
        val removed = reminders.removeIf { it.id.equals(id, ignoreCase = true) }
        if (removed) persist()
        return removed
    }

    fun update(updated: Reminder) {
        val index = reminders.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            reminders[index] = updated
            persist()
        }
    }

    /**
     * Called by the background scheduler coroutine.
     * Finds every due recurring reminder, advances it to the next occurrence,
     * persists the change, and returns a list of [TriggerEvent] records — one per reminder fired.
     * One-time (NONE) reminders are left untouched; they require an explicit dismiss_reminder call.
     */
    fun autoAdvanceRecurring(): List<TriggerEvent> {
        val now = System.currentTimeMillis()
        val triggered = mutableListOf<TriggerEvent>()

        for (i in reminders.indices) {
            val r = reminders[i]
            if (!r.isDismissed && r.scheduledAt <= now && r.recurrence != Recurrence.NONE) {
                val advanced = advanceToNext(r, now)
                reminders[i] = advanced
                triggered.add(TriggerEvent(r.id, r.title, now, advanced.scheduledAt))
            }
        }

        if (triggered.isNotEmpty()) persist()
        return triggered
    }

    private fun advanceToNext(reminder: Reminder, triggeredAt: Long): Reminder {
        val nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(triggeredAt), ZoneId.systemDefault())
        var ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(reminder.scheduledAt), ZoneId.systemDefault())
        val interval = reminder.recurrenceIntervalDays.toLong()
        // Skip all missed occurrences — advance directly to the next future date
        do {
            ldt = when (reminder.recurrence) {
                Recurrence.DAILY -> ldt.plusDays(interval)
                Recurrence.WEEKLY -> ldt.plusWeeks(interval)
                Recurrence.MONTHLY -> ldt.plusMonths(interval)
                Recurrence.NONE -> ldt
            }
        } while (!ldt.isAfter(nowLdt))
        val nextMillis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return reminder.copy(scheduledAt = nextMillis, lastTriggeredAt = triggeredAt)
    }

    private fun load() {
        if (!storageFile.exists()) return
        try {
            val raw = storageFile.readText()
            if (raw.isBlank()) return
            val loaded = json.decodeFromString(ListSerializer(Reminder.serializer()), raw)
            reminders.addAll(loaded)
        } catch (e: Exception) {
            System.err.println("[ReminderStorage] Failed to load: ${e.message}")
        }
    }

    private fun persist() {
        try {
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(
                json.encodeToString(ListSerializer(Reminder.serializer()), reminders)
            )
        } catch (e: Exception) {
            System.err.println("[ReminderStorage] Failed to save: ${e.message}")
        }
    }

    companion object {
        fun defaultStorageFile(): File =
            File(System.getProperty("user.home"), ".llmchat/reminders.json")
    }
}
