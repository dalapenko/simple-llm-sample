package mcpscheduler

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderStorageTest {

    private lateinit var tempFile: File
    private lateinit var storage: ReminderStorage

    @BeforeTest
    fun setup() {
        tempFile = Files.createTempFile("reminders-test", ".json").toFile()
        tempFile.delete() // ensure fresh start (no file = empty storage)
        storage = ReminderStorage(tempFile)
    }

    @AfterTest
    fun teardown() {
        tempFile.delete()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun reminder(
        title: String = "Test",
        scheduledAt: Long = System.currentTimeMillis() - 1_000L, // past by default
        recurrence: Recurrence = Recurrence.NONE,
        isDismissed: Boolean = false
    ) = Reminder(
        id = Reminder.generateId(),
        title = title,
        scheduledAt = scheduledAt,
        recurrence = recurrence,
        isDismissed = isDismissed
    )

    // ── add ───────────────────────────────────────────────────────────────────

    @Test
    fun `add stores reminder and returns it`() {
        val r = reminder("My Task")
        val returned = storage.add(r)
        assertEquals(r, returned)
        assertEquals(1, storage.list(true).size)
    }

    @Test
    fun `add persists reminder to disk`() {
        storage.add(reminder("Persisted"))
        val fresh = ReminderStorage(tempFile)
        assertEquals(1, fresh.list(true).size)
        assertEquals("Persisted", fresh.list(true).first().title)
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    fun `list excludes dismissed reminders by default`() {
        storage.add(reminder("Active"))
        storage.add(reminder("Dismissed", isDismissed = true))
        val result = storage.list(false)
        assertEquals(1, result.size)
        assertEquals("Active", result.first().title)
    }

    @Test
    fun `list includes dismissed when requested`() {
        storage.add(reminder("Active"))
        storage.add(reminder("Dismissed", isDismissed = true))
        assertEquals(2, storage.list(true).size)
    }

    @Test
    fun `list returns empty when no reminders exist`() {
        assertTrue(storage.list(false).isEmpty())
    }

    // ── getDue ────────────────────────────────────────────────────────────────

    @Test
    fun `getDue returns past-scheduled reminders`() {
        val past = reminder(scheduledAt = System.currentTimeMillis() - 5_000L)
        val future = reminder(scheduledAt = System.currentTimeMillis() + 100_000L)
        storage.add(past)
        storage.add(future)
        val due = storage.getDue()
        assertEquals(1, due.size)
        assertEquals(past.id, due.first().id)
    }

    @Test
    fun `getDue excludes dismissed reminders`() {
        storage.add(reminder(scheduledAt = System.currentTimeMillis() - 1_000L, isDismissed = true))
        assertTrue(storage.getDue().isEmpty())
    }

    @Test
    fun `getDue excludes future reminders`() {
        storage.add(reminder(scheduledAt = System.currentTimeMillis() + 100_000L))
        assertTrue(storage.getDue().isEmpty())
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    fun `findById returns reminder by exact ID`() {
        val r = reminder().copy(id = "REM-ABCDEF")
        storage.add(r)
        assertNotNull(storage.findById("REM-ABCDEF"))
    }

    @Test
    fun `findById is case insensitive`() {
        storage.add(reminder().copy(id = "REM-ABCDEF"))
        assertNotNull(storage.findById("rem-abcdef"))
        assertNotNull(storage.findById("REM-ABCDEF"))
        assertNotNull(storage.findById("Rem-AbCdEf"))
    }

    @Test
    fun `findById returns null for missing ID`() {
        assertNull(storage.findById("REM-NOTEXIST"))
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    fun `cancel removes reminder and returns true`() {
        val r = storage.add(reminder("To Cancel"))
        assertTrue(storage.cancel(r.id))
        assertTrue(storage.list(true).isEmpty())
    }

    @Test
    fun `cancel persists removal to disk`() {
        val r = storage.add(reminder("To Cancel"))
        storage.cancel(r.id)
        val fresh = ReminderStorage(tempFile)
        assertTrue(fresh.list(true).isEmpty())
    }

    @Test
    fun `cancel returns false for missing ID`() {
        assertFalse(storage.cancel("REM-NOTEXIST"))
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun `update replaces existing reminder`() {
        val r = storage.add(reminder("Original"))
        storage.update(r.copy(title = "Updated", isDismissed = true))
        val found = storage.findById(r.id)!!
        assertEquals("Updated", found.title)
        assertTrue(found.isDismissed)
    }

    @Test
    fun `update persists change to disk`() {
        val r = storage.add(reminder("Original"))
        storage.update(r.copy(title = "Updated"))
        val fresh = ReminderStorage(tempFile)
        assertEquals("Updated", fresh.findById(r.id)!!.title)
    }

    @Test
    fun `update does nothing for unknown ID`() {
        val fake = Reminder(id = "REM-NOTEXIST", title = "Ghost", scheduledAt = 1000L)
        storage.update(fake) // should not throw
        assertTrue(storage.list(true).isEmpty())
    }

    // ── autoAdvanceRecurring ──────────────────────────────────────────────────

    @Test
    fun `autoAdvanceRecurring advances daily reminder to future`() {
        val now = System.currentTimeMillis()
        val r = storage.add(reminder(
            scheduledAt = now - 24 * 3_600_000L, // 1 day in the past
            recurrence = Recurrence.DAILY
        ))
        val events = storage.autoAdvanceRecurring()
        assertEquals(1, events.size)
        assertEquals(r.id, events.first().reminderId)
        val advanced = storage.findById(r.id)!!
        assertTrue(advanced.scheduledAt > now, "Reminder should be rescheduled to the future")
    }

    @Test
    fun `autoAdvanceRecurring advances weekly reminder to future`() {
        val now = System.currentTimeMillis()
        val r = storage.add(reminder(
            scheduledAt = now - 7 * 24 * 3_600_000L, // 1 week in the past
            recurrence = Recurrence.WEEKLY
        ))
        val events = storage.autoAdvanceRecurring()
        assertEquals(1, events.size)
        val advanced = storage.findById(r.id)!!
        assertTrue(advanced.scheduledAt > now)
    }

    @Test
    fun `autoAdvanceRecurring advances monthly reminder to future`() {
        val now = System.currentTimeMillis()
        val r = storage.add(reminder(
            scheduledAt = now - 31 * 24 * 3_600_000L, // ~1 month in the past
            recurrence = Recurrence.MONTHLY
        ))
        val events = storage.autoAdvanceRecurring()
        assertEquals(1, events.size)
        val advanced = storage.findById(r.id)!!
        assertTrue(advanced.scheduledAt > now)
    }

    @Test
    fun `autoAdvanceRecurring skips missed occurrences in one call`() {
        val now = System.currentTimeMillis()
        // 3 days past — DAILY should skip to next future occurrence, not just +1 day
        val r = storage.add(reminder(
            scheduledAt = now - 3 * 24 * 3_600_000L,
            recurrence = Recurrence.DAILY
        ))
        storage.autoAdvanceRecurring()
        val advanced = storage.findById(r.id)!!
        // Next occurrence must be in the future, not still in the past
        assertTrue(advanced.scheduledAt > now)
    }

    @Test
    fun `autoAdvanceRecurring does not advance one-time reminders`() {
        storage.add(reminder(recurrence = Recurrence.NONE))
        val events = storage.autoAdvanceRecurring()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `autoAdvanceRecurring does not advance dismissed recurring reminders`() {
        storage.add(reminder(recurrence = Recurrence.DAILY, isDismissed = true))
        val events = storage.autoAdvanceRecurring()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `autoAdvanceRecurring does not advance future reminders`() {
        storage.add(reminder(
            scheduledAt = System.currentTimeMillis() + 100_000L,
            recurrence = Recurrence.DAILY
        ))
        val events = storage.autoAdvanceRecurring()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `autoAdvanceRecurring returns TriggerEvent with correct reminder metadata`() {
        val now = System.currentTimeMillis()
        val r = storage.add(reminder(
            title = "Daily Stand-up",
            scheduledAt = now - 24 * 3_600_000L,
            recurrence = Recurrence.DAILY
        ))
        val events = storage.autoAdvanceRecurring()
        val event = events.first()
        assertEquals(r.id, event.reminderId)
        assertEquals("Daily Stand-up", event.title)
        assertTrue(event.triggeredAt >= now)
        assertTrue(event.nextScheduledAt > now)
    }

    // ── persistence round-trip ────────────────────────────────────────────────

    @Test
    fun `data survives full persistence round-trip`() {
        storage.add(Reminder(
            id = "REM-PERSIST",
            title = "Survives reload",
            description = "With description",
            scheduledAt = 9_999_999L,
            recurrence = Recurrence.WEEKLY,
            recurrenceIntervalDays = 2
        ))
        val fresh = ReminderStorage(tempFile)
        val loaded = fresh.findById("REM-PERSIST")!!
        assertEquals("Survives reload", loaded.title)
        assertEquals("With description", loaded.description)
        assertEquals(9_999_999L, loaded.scheduledAt)
        assertEquals(Recurrence.WEEKLY, loaded.recurrence)
        assertEquals(2, loaded.recurrenceIntervalDays)
    }

    @Test
    fun `empty storage file loads without error`() {
        val empty = ReminderStorage(tempFile) // tempFile doesn't exist
        assertTrue(empty.list(true).isEmpty())
    }
}
