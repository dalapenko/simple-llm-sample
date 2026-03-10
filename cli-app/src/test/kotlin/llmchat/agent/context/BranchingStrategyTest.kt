package llmchat.agent.context

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchingStrategyTest {

    // ── Initialization ───────────────────────────────────────────────────────

    @Test
    fun `starts with main branch as current`() {
        val s = BranchingStrategy()
        assertEquals("main", s.currentBranchName())
    }

    @Test
    fun `listBranches returns only main on init`() {
        val s = BranchingStrategy()
        assertEquals(1, s.listBranches().size)
        assertEquals("main", s.listBranches()[0].name)
    }

    @Test
    fun `listCheckpoints returns empty list on init`() {
        val s = BranchingStrategy()
        assertTrue(s.listCheckpoints().isEmpty())
    }

    // ── addMessage ───────────────────────────────────────────────────────────

    @Test
    fun `addMessage appends exchange to current branch`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("question", "answer")
        assertEquals(1, s.currentBranch.messages.size)
        assertEquals("question" to "answer", s.currentBranch.messages[0])
    }

    // ── buildContextBlock ────────────────────────────────────────────────────

    @Test
    fun `buildContextBlock is empty when no messages`() {
        assertTrue(BranchingStrategy().buildContextBlock().isEmpty())
    }

    @Test
    fun `buildContextBlock includes branch name and messages`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("hello", "hi")
        val block = s.buildContextBlock()
        assertContains(block, "[Branch: main]")
        assertContains(block, "hello")
        assertContains(block, "hi")
    }

    @Test
    fun `buildContextBlock respects windowSize`() = runTest {
        val s = BranchingStrategy(windowSize = 2)
        repeat(4) { i -> s.addMessage("q$i", "a$i") }
        val block = s.buildContextBlock()
        assertFalse(block.contains("q0"))
        assertFalse(block.contains("q1"))
        assertContains(block, "q2")
        assertContains(block, "q3")
    }

    // ── clearHistory ─────────────────────────────────────────────────────────

    @Test
    fun `clearHistory resets to main branch only`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("q", "a")
        s.createBranch("feature")
        s.saveCheckpoint("cp1")
        s.clearHistory()
        assertEquals("main", s.currentBranchName())
        assertEquals(1, s.listBranches().size)
        assertTrue(s.listCheckpoints().isEmpty())
        assertTrue(s.currentBranch.messages.isEmpty())
    }

    // ── loadMessages ─────────────────────────────────────────────────────────

    @Test
    fun `loadMessages loads into main branch and switches to it`() = runTest {
        val s = BranchingStrategy()
        s.createBranch("other")
        s.loadMessages(listOf("restored q" to "restored a"))
        assertEquals("main", s.currentBranchName())
        assertEquals(listOf("restored q" to "restored a"), s.currentBranch.messages)
    }

    // ── createBranch ─────────────────────────────────────────────────────────

    @Test
    fun `createBranch creates new branch and switches to it`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("q", "a")
        val branch = s.createBranch("feature")
        assertEquals("feature", branch.name)
        assertEquals("feature", s.currentBranchName())
        assertEquals(2, s.listBranches().size)
    }

    @Test
    fun `createBranch without checkpoint seeds from current branch messages`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("shared q", "shared a")
        val branch = s.createBranch("fork")
        assertEquals(1, branch.messages.size)
        assertEquals("shared q", branch.messages[0].first)
    }

    @Test
    fun `createBranch from checkpoint seeds correct messages`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("q0", "a0")
        s.addMessage("q1", "a1")
        s.saveCheckpoint("after-two")
        s.addMessage("q2", "a2") // not in checkpoint

        val branch = s.createBranch("from-cp", fromCheckpointName = "after-two")
        assertEquals(2, branch.messages.size)
        assertEquals("q0", branch.messages[0].first)
        assertEquals("q1", branch.messages[1].first)
    }

    @Test
    fun `createBranch throws for duplicate name`() = runTest {
        val s = BranchingStrategy()
        s.createBranch("feature")
        assertFailsWith<IllegalArgumentException> {
            s.createBranch("feature")
        }
    }

    @Test
    fun `createBranch throws for blank name`() {
        assertFailsWith<IllegalArgumentException> {
            BranchingStrategy().createBranch("")
        }
    }

    @Test
    fun `createBranch throws for unknown checkpoint name`() {
        assertFailsWith<IllegalArgumentException> {
            BranchingStrategy().createBranch("fork", fromCheckpointName = "nonexistent")
        }
    }

    // ── switchBranch ─────────────────────────────────────────────────────────

    @Test
    fun `switchBranch by name changes current branch`() = runTest {
        val s = BranchingStrategy()
        s.createBranch("feature")
        s.switchBranch("main")
        assertEquals("main", s.currentBranchName())
    }

    @Test
    fun `switchBranch by id changes current branch`() = runTest {
        val s = BranchingStrategy()
        val branch = s.createBranch("feature")
        s.switchBranch("main")
        s.switchBranch(branch.id)
        assertEquals("feature", s.currentBranchName())
    }

    @Test
    fun `switchBranch throws for unknown branch`() {
        assertFailsWith<IllegalArgumentException> {
            BranchingStrategy().switchBranch("nonexistent")
        }
    }

    // ── saveCheckpoint ───────────────────────────────────────────────────────

    @Test
    fun `saveCheckpoint records current message count`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("q1", "a1")
        s.addMessage("q2", "a2")
        val cp = s.saveCheckpoint("mid-point")
        assertEquals("mid-point", cp.name)
        assertEquals("main", cp.branchId)
        assertEquals(2, cp.messageCount)
    }

    @Test
    fun `saveCheckpoint throws for blank name`() {
        assertFailsWith<IllegalArgumentException> {
            BranchingStrategy().saveCheckpoint("")
        }
    }

    @Test
    fun `multiple checkpoints are listed in order`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("q", "a")
        s.saveCheckpoint("cp1")
        s.saveCheckpoint("cp2")
        val cps = s.listCheckpoints()
        assertEquals(2, cps.size)
        assertEquals("cp1", cps[0].name)
        assertEquals("cp2", cps[1].name)
    }

    // ── estimateTokenStats ───────────────────────────────────────────────────

    @Test
    fun `estimateTokenStats returns zero for empty branch`() {
        val stats = BranchingStrategy().estimateTokenStats()
        assertEquals(0, stats.primary)
    }

    @Test
    fun `estimateTokenStats counts tokens in current branch`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("aaaa", "bbbb") // 1 + 1 = 2 tokens
        val stats = s.estimateTokenStats()
        assertEquals(2, stats.primary)
    }

    @Test
    fun `estimateTokenStats reflects current branch after switch`() = runTest {
        val s = BranchingStrategy()
        s.addMessage("aaaa", "bbbb") // 2 tokens in main
        s.createBranch("empty-branch")
        // empty-branch has seeded messages from main (1 exchange = 2 tokens)
        s.switchBranch("main")
        val stats = s.estimateTokenStats()
        assertEquals(2, stats.primary)
    }
}
