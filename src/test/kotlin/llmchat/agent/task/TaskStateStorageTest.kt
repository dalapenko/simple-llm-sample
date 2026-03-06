package llmchat.agent.task

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for TaskStateStorage.
 *
 * TaskStateStorage is a Kotlin object (singleton) with a hardcoded file path
 * (~/.llmchat/task-state.json). Since the backing field is a JVM `final` field
 * (Kotlin `val`), reflection-based redirection is blocked in Java 17+.
 *
 * We test against the real storage file, saving and restoring its content
 * in @BeforeEach/@AfterEach to avoid side effects on the user's environment.
 */
class TaskStateStorageTest {

    private val storageFile = File(System.getProperty("user.home"), ".llmchat/task-state.json")
    private var savedContent: String? = null

    @BeforeEach
    fun backupAndClear() {
        savedContent = if (storageFile.exists()) storageFile.readText() else null
        storageFile.delete()
    }

    @AfterEach
    fun restore() {
        storageFile.delete()
        if (savedContent != null) {
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(savedContent!!)
        }
    }

    private fun sampleState(): TaskState = TaskFSM.create("test task").getState()

    @Test
    fun `load returns null when file does not exist`() {
        assertNull(TaskStateStorage.load())
    }

    @Test
    fun `save and load round-trips task state`() {
        val state = sampleState()
        TaskStateStorage.save(state)

        val loaded = TaskStateStorage.load()
        assertNotNull(loaded)
        assertEquals(state.id, loaded.id)
        assertEquals(state.taskDescription, loaded.taskDescription)
        assertEquals(state.stage, loaded.stage)
    }

    @Test
    fun `save persists all fields including history`() {
        val fsm = TaskFSM.create("full task")
        fsm.transition(TaskStage.PLAN_APPROVED, "plan ready")
        fsm.transition(TaskStage.EXECUTION, "coding", ExpectedAction.TOOL_EXECUTION)
        val state = fsm.getState()

        TaskStateStorage.save(state)
        val loaded = TaskStateStorage.load()

        assertNotNull(loaded)
        assertEquals(TaskStage.EXECUTION, loaded.stage)
        assertEquals("coding", loaded.currentStep)
        assertEquals(ExpectedAction.TOOL_EXECUTION, loaded.expectedAction)
        assertEquals(2, loaded.history.size)
        assertEquals(TaskStage.PLANNING, loaded.history[0].fromStage)
        assertEquals(TaskStage.PLAN_APPROVED, loaded.history[0].toStage)
        assertEquals(TaskStage.PLAN_APPROVED, loaded.history[1].fromStage)
        assertEquals(TaskStage.EXECUTION, loaded.history[1].toStage)
    }

    @Test
    fun `save overwrites previous state`() {
        TaskStateStorage.save(TaskFSM.create("first task").getState())
        TaskStateStorage.save(TaskFSM.create("second task").getState())

        val loaded = TaskStateStorage.load()
        assertEquals("second task", loaded?.taskDescription)
    }

    @Test
    fun `clear removes the state file`() {
        TaskStateStorage.save(sampleState())
        assertTrue(storageFile.exists())

        TaskStateStorage.clear()
        assertFalse(storageFile.exists())
    }

    @Test
    fun `load returns null after clear`() {
        TaskStateStorage.save(sampleState())
        TaskStateStorage.clear()
        assertNull(TaskStateStorage.load())
    }

    @Test
    fun `hasActiveState returns false when no file`() {
        assertFalse(TaskStateStorage.hasActiveState())
    }

    @Test
    fun `hasActiveState returns true after save`() {
        TaskStateStorage.save(sampleState())
        assertTrue(TaskStateStorage.hasActiveState())
    }

    @Test
    fun `hasActiveState returns false after clear`() {
        TaskStateStorage.save(sampleState())
        TaskStateStorage.clear()
        assertFalse(TaskStateStorage.hasActiveState())
    }

    @Test
    fun `load returns null for blank file`() {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText("   ")
        assertNull(TaskStateStorage.load())
    }

    @Test
    fun `load returns null for corrupted file`() {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText("not valid json }{")
        assertNull(TaskStateStorage.load())
    }
}
