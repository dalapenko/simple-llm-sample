package mcpscheduler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderTest {

    @Test
    fun `generateId returns REM- prefix with 6 uppercase hex chars`() {
        val id = Reminder.generateId()
        assertTrue(id.matches(Regex("REM-[A-F0-9]{6}")), "ID '$id' does not match REM-XXXXXX format")
    }

    @Test
    fun `generateId returns unique IDs`() {
        val ids = (1..100).map { Reminder.generateId() }.toSet()
        assertEquals(100, ids.size)
    }

    @Test
    fun `Reminder has correct default field values`() {
        val reminder = Reminder(id = "REM-000001", title = "Test", scheduledAt = 1000L)

        assertEquals("", reminder.description)
        assertEquals(Recurrence.NONE, reminder.recurrence)
        assertEquals(1, reminder.recurrenceIntervalDays)
        assertFalse(reminder.isDismissed)
        assertNull(reminder.lastTriggeredAt)
    }

    @Test
    fun `Reminder copy preserves all fields except overridden ones`() {
        val original = Reminder(id = "REM-AABBCC", title = "Original", scheduledAt = 5000L)
        val copy = original.copy(title = "Updated", isDismissed = true)

        assertEquals("REM-AABBCC", copy.id)
        assertEquals("Updated", copy.title)
        assertEquals(5000L, copy.scheduledAt)
        assertTrue(copy.isDismissed)
    }

    @Test
    fun `Reminder data class equality works correctly`() {
        val r1 = Reminder(id = "REM-123456", title = "Same", scheduledAt = 1000L)
        val r2 = Reminder(id = "REM-123456", title = "Same", scheduledAt = 1000L)
        assertEquals(r1, r2)
    }

    @Test
    fun `Reminders with different IDs are not equal`() {
        val r1 = Reminder(id = "REM-AAAAAA", title = "Same", scheduledAt = 1000L)
        val r2 = Reminder(id = "REM-BBBBBB", title = "Same", scheduledAt = 1000L)
        assertNotEquals(r1, r2)
    }

    @Test
    fun `TriggerEvent holds correct data`() {
        val event = TriggerEvent(
            reminderId = "REM-111111",
            title = "Stand-up",
            triggeredAt = 1_000L,
            nextScheduledAt = 86_400_000L
        )

        assertEquals("REM-111111", event.reminderId)
        assertEquals("Stand-up", event.title)
        assertEquals(1_000L, event.triggeredAt)
        assertEquals(86_400_000L, event.nextScheduledAt)
    }
}
