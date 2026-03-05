package llmchat.agent.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertContains

class TaskFSMTest {

    @Test
    fun `create initializes FSM in PLANNING stage`() {
        val fsm = TaskFSM.create("implement feature X")
        assertEquals(TaskStage.PLANNING, fsm.getState().stage)
    }

    @Test
    fun `create sets task description`() {
        val fsm = TaskFSM.create("fix the bug")
        assertEquals("fix the bug", fsm.getState().taskDescription)
    }

    @Test
    fun `create initializes with empty history`() {
        val fsm = TaskFSM.create("task")
        assertTrue(fsm.getState().history.isEmpty())
    }

    @Test
    fun `create sets expectedAction to LLM_GENERATION`() {
        val fsm = TaskFSM.create("task")
        assertEquals(ExpectedAction.LLM_GENERATION, fsm.getState().expectedAction)
    }

    @Test
    fun `create generates non-blank id`() {
        val fsm = TaskFSM.create("task")
        assertTrue(fsm.getState().id.isNotBlank())
    }

    @Test
    fun `create sets initial step to Initial planning`() {
        val fsm = TaskFSM.create("task")
        assertEquals("Initial planning", fsm.getState().currentStep)
    }

    // --- Valid transitions ---

    @Test
    fun `PLANNING to EXECUTION is valid`() {
        val fsm = TaskFSM.create("task")
        val result = fsm.transition(TaskStage.EXECUTION)
        assertTrue(result.isSuccess)
        assertEquals(TaskStage.EXECUTION, result.getOrNull()?.stage)
    }

    @Test
    fun `PLANNING to ERROR is valid`() {
        val fsm = TaskFSM.create("task")
        val result = fsm.transition(TaskStage.ERROR)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `EXECUTION to VALIDATION is valid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        val result = fsm.transition(TaskStage.VALIDATION)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `EXECUTION to PLANNING is valid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        val result = fsm.transition(TaskStage.PLANNING)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `EXECUTION to ERROR is valid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        val result = fsm.transition(TaskStage.ERROR)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `VALIDATION to DONE is valid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        fsm.transition(TaskStage.VALIDATION)
        val result = fsm.transition(TaskStage.DONE)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `VALIDATION to PLANNING is valid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        fsm.transition(TaskStage.VALIDATION)
        val result = fsm.transition(TaskStage.PLANNING)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `VALIDATION to EXECUTION is valid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        fsm.transition(TaskStage.VALIDATION)
        val result = fsm.transition(TaskStage.EXECUTION)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ERROR to PLANNING is valid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.ERROR)
        val result = fsm.transition(TaskStage.PLANNING)
        assertTrue(result.isSuccess)
    }

    // --- Invalid transitions ---

    @Test
    fun `PLANNING to DONE is invalid`() {
        val fsm = TaskFSM.create("task")
        val result = fsm.transition(TaskStage.DONE)
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message ?: "", "PLANNING")
    }

    @Test
    fun `PLANNING to VALIDATION is invalid`() {
        val fsm = TaskFSM.create("task")
        val result = fsm.transition(TaskStage.VALIDATION)
        assertTrue(result.isFailure)
    }

    @Test
    fun `DONE is terminal — no transitions allowed`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        fsm.transition(TaskStage.VALIDATION)
        fsm.transition(TaskStage.DONE)

        for (target in TaskStage.entries) {
            val result = fsm.transition(target)
            assertTrue(result.isFailure, "Expected failure for DONE → $target")
        }
    }

    @Test
    fun `ERROR to EXECUTION is invalid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.ERROR)
        val result = fsm.transition(TaskStage.EXECUTION)
        assertTrue(result.isFailure)
    }

    @Test
    fun `ERROR to DONE is invalid`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.ERROR)
        val result = fsm.transition(TaskStage.DONE)
        assertTrue(result.isFailure)
    }

    @Test
    fun `invalid transition does not change state`() {
        val fsm = TaskFSM.create("task")
        val stateBefore = fsm.getState()

        fsm.transition(TaskStage.DONE) // invalid

        assertEquals(stateBefore.stage, fsm.getState().stage)
        assertEquals(stateBefore.history.size, fsm.getState().history.size)
    }

    // --- History recording ---

    @Test
    fun `transition records history entry`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION, "writing code")

        val history = fsm.getState().history
        assertEquals(1, history.size)
        assertEquals(TaskStage.PLANNING, history[0].fromStage)
        assertEquals(TaskStage.EXECUTION, history[0].toStage)
        assertEquals("writing code", history[0].step)
    }

    @Test
    fun `multiple transitions accumulate history`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION, "step 1")
        fsm.transition(TaskStage.VALIDATION, "step 2")
        fsm.transition(TaskStage.DONE, "step 3")

        assertEquals(3, fsm.getState().history.size)
    }

    @Test
    fun `history entry has positive timestamp`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        assertTrue(fsm.getState().history[0].timestamp > 0)
    }

    // --- updateStep ---

    @Test
    fun `updateStep changes currentStep without stage change`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION)
        fsm.updateStep("refining implementation", ExpectedAction.TOOL_EXECUTION)

        assertEquals(TaskStage.EXECUTION, fsm.getState().stage)
        assertEquals("refining implementation", fsm.getState().currentStep)
        assertEquals(ExpectedAction.TOOL_EXECUTION, fsm.getState().expectedAction)
    }

    @Test
    fun `updateStep does not add to history`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION) // history.size = 1
        fsm.updateStep("new step")

        assertEquals(1, fsm.getState().history.size)
    }

    @Test
    fun `updateStep uses LLM_GENERATION as default expectedAction`() {
        val fsm = TaskFSM.create("task")
        fsm.updateStep("some step")
        assertEquals(ExpectedAction.LLM_GENERATION, fsm.getState().expectedAction)
    }

    // --- transition fields ---

    @Test
    fun `transition updates currentStep and expectedAction`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION, "implementing", ExpectedAction.USER_APPROVAL)

        assertEquals("implementing", fsm.getState().currentStep)
        assertEquals(ExpectedAction.USER_APPROVAL, fsm.getState().expectedAction)
    }

    @Test
    fun `transition updates updatedAt timestamp`() {
        val fsm = TaskFSM.create("task")
        val before = fsm.getState().updatedAt
        Thread.sleep(2)
        fsm.transition(TaskStage.EXECUTION)

        assertTrue(fsm.getState().updatedAt >= before)
    }

    @Test
    fun `successful transition returns updated state`() {
        val fsm = TaskFSM.create("task")
        val result = fsm.transition(TaskStage.EXECUTION, "start")
        val returned = result.getOrNull()
        assertNotNull(returned)
        assertEquals(TaskStage.EXECUTION, returned.stage)
        assertEquals("start", returned.currentStep)
    }

    // --- buildResumptionContext ---

    @Test
    fun `buildResumptionContext contains task description`() {
        val fsm = TaskFSM.create("build the payment module")
        val ctx = fsm.buildResumptionContext()
        assertContains(ctx, "build the payment module")
    }

    @Test
    fun `buildResumptionContext contains stage name`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION, "coding")
        val ctx = fsm.buildResumptionContext()
        assertContains(ctx, "EXECUTION")
        assertContains(ctx, "Execution")
    }

    @Test
    fun `buildResumptionContext contains current step`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION, "writing unit tests")
        val ctx = fsm.buildResumptionContext()
        assertContains(ctx, "writing unit tests")
    }

    @Test
    fun `buildResumptionContext contains expected action`() {
        val fsm = TaskFSM.create("task")
        fsm.transition(TaskStage.EXECUTION, "", ExpectedAction.USER_INPUT)
        val ctx = fsm.buildResumptionContext()
        assertContains(ctx, "USER_INPUT")
    }

    @Test
    fun `buildResumptionContext contains ACTIVE TASK markers`() {
        val fsm = TaskFSM.create("task")
        val ctx = fsm.buildResumptionContext()
        assertContains(ctx, "[ACTIVE TASK]")
        assertContains(ctx, "[END ACTIVE TASK]")
    }
}
