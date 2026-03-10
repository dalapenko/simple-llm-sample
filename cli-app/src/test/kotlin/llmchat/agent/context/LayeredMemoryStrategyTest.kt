package llmchat.agent.context

import kotlinx.coroutines.runBlocking
import llmchat.agent.memory.LongTermStore
import llmchat.agent.memory.MemoryLayer
import llmchat.agent.memory.ShortTermStore
import llmchat.agent.memory.WorkMemoryStore
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayeredMemoryStrategyTest {

    @TempDir
    lateinit var tempDir: File

    private fun strategy(): LayeredMemoryStrategy {
        val ltStore = LongTermStore(storageFile = File(tempDir, "long-term-memory.json"))
        return LayeredMemoryStrategy(
            windowSize = 3,
            shortTermStore = ShortTermStore(maxItems = 3),
            workStore = WorkMemoryStore(),
            longTermStore = ltStore
        )
    }

    // ── Context block building ───────────────────────────────────────────────

    @Test
    fun `buildContextBlock is empty when all layers are empty`() {
        val result = strategy().buildContextBlock()
        assertTrue(result.isEmpty(), "Expected empty context block, got: $result")
    }

    @Test
    fun `buildContextBlock includes long-term section when populated`() {
        val s = strategy()
        s.addToLayer(MemoryLayer.LONG_TERM, "User prefers Kotlin")
        val block = s.buildContextBlock()
        assertContains(block, "[LONG-TERM KNOWLEDGE]")
        assertContains(block, "User prefers Kotlin")
    }

    @Test
    fun `buildContextBlock includes work memory section`() {
        val s = strategy()
        s.addToLayer(MemoryLayer.WORK, "Designing the auth schema")
        val block = s.buildContextBlock()
        assertContains(block, "[CURRENT TASK]")
        assertContains(block, "Designing the auth schema")
    }

    @Test
    fun `buildContextBlock includes short-term notes section`() {
        val s = strategy()
        s.addToLayer(MemoryLayer.SHORT_TERM, "Remember: use snake_case")
        val block = s.buildContextBlock()
        assertContains(block, "[SHORT-TERM NOTES]")
        assertContains(block, "Remember: use snake_case")
    }

    @Test
    fun `buildContextBlock includes conversation exchanges`() = runBlocking {
        val s = strategy()
        s.addMessage("What is Kotlin?", "Kotlin is a JVM language.")
        val block = s.buildContextBlock()
        assertContains(block, "[RECENT CONVERSATION]")
        assertContains(block, "What is Kotlin?")
        assertContains(block, "Kotlin is a JVM language.")
    }

    @Test
    fun `buildContextBlock ordering is long-term then work then short-term then conversation`() = runBlocking {
        val s = strategy()
        s.addToLayer(MemoryLayer.LONG_TERM, "LT fact")
        s.addToLayer(MemoryLayer.WORK, "Work note")
        s.addToLayer(MemoryLayer.SHORT_TERM, "ST note")
        s.addMessage("hello", "hi")

        val block = s.buildContextBlock()
        val ltIdx = block.indexOf("[LONG-TERM KNOWLEDGE]")
        val workIdx = block.indexOf("[CURRENT TASK]")
        val stIdx = block.indexOf("[SHORT-TERM NOTES]")
        val convIdx = block.indexOf("[RECENT CONVERSATION]")

        assertTrue(ltIdx < workIdx, "Long-term should precede work")
        assertTrue(workIdx < stIdx, "Work should precede short-term")
        assertTrue(stIdx < convIdx, "Short-term should precede conversation")
    }

    // ── Layer isolation ─────────────────────────────────────────────────────

    @Test
    fun `clearHistory clears conversation and short-term and work but not long-term`() = runBlocking {
        val s = strategy()
        s.addMessage("q", "a")
        s.addToLayer(MemoryLayer.SHORT_TERM, "note")
        s.addToLayer(MemoryLayer.WORK, "task")
        s.addToLayer(MemoryLayer.LONG_TERM, "persistent")

        s.clearHistory()

        val block = s.buildContextBlock()
        assertFalse(block.contains("[RECENT CONVERSATION]"), "Conversation should be cleared")
        assertFalse(block.contains("[SHORT-TERM NOTES]"), "Short-term should be cleared")
        assertFalse(block.contains("[CURRENT TASK]"), "Work memory should be cleared")
        assertContains(block, "[LONG-TERM KNOWLEDGE]")
        assertContains(block, "persistent")
    }

    @Test
    fun `clearLayer only affects specified layer`() {
        val s = strategy()
        s.addToLayer(MemoryLayer.WORK, "task data")
        s.addToLayer(MemoryLayer.LONG_TERM, "persistent data")

        s.clearLayer(MemoryLayer.WORK)

        assertTrue(s.listLayer(MemoryLayer.WORK).isEmpty())
        assertEquals(1, s.listLayer(MemoryLayer.LONG_TERM).size)
    }

    @Test
    fun `deleteFromLayer removes correct item`() {
        val s = strategy()
        val item1 = s.addToLayer(MemoryLayer.WORK, "first")
        s.addToLayer(MemoryLayer.WORK, "second")

        val removed = s.deleteFromLayer(MemoryLayer.WORK, item1.id)

        assertTrue(removed)
        val remaining = s.listLayer(MemoryLayer.WORK)
        assertEquals(1, remaining.size)
        assertEquals("second", remaining[0].content)
    }

    @Test
    fun `deleteFromLayer returns false for unknown id`() {
        val s = strategy()
        assertFalse(s.deleteFromLayer(MemoryLayer.SHORT_TERM, "nonexistent"))
    }

    // ── Sliding window behaviour ─────────────────────────────────────────────

    @Test
    fun `conversation window respects windowSize`() = runBlocking {
        val s = strategy() // windowSize = 3
        repeat(5) { i -> s.addMessage("q$i", "a$i") }
        val block = s.buildContextBlock()
        // Should only contain the last 3 exchanges
        assertFalse(block.contains("q0"), "Oldest exchange should be evicted")
        assertFalse(block.contains("q1"), "Second oldest should be evicted")
        assertContains(block, "q2")
        assertContains(block, "q3")
        assertContains(block, "q4")
    }

    @Test
    fun `short-term store respects maxItems sliding window`() {
        val s = strategy() // maxItems = 3
        repeat(5) { i -> s.addToLayer(MemoryLayer.SHORT_TERM, "note$i") }
        val items = s.listLayer(MemoryLayer.SHORT_TERM)
        assertEquals(3, items.size)
        assertEquals("note2", items[0].content)
        assertEquals("note4", items[2].content)
    }

    // ── Token stats ──────────────────────────────────────────────────────────

    @Test
    fun `estimateTokenStats returns zero for empty strategy`() {
        val stats = strategy().estimateTokenStats()
        assertEquals(0, stats.primary)
        assertEquals(0, stats.secondary)
        assertEquals(0, stats.tertiary)
    }

    @Test
    fun `estimateTokenStats separates layers correctly`() = runBlocking {
        val s = strategy()
        s.addMessage("hello world", "hi there") // → primary
        s.addToLayer(MemoryLayer.WORK, "task context text") // → secondary
        s.addToLayer(MemoryLayer.LONG_TERM, "long term knowledge") // → tertiary

        val stats = s.estimateTokenStats()
        assertTrue(stats.primary > 0, "Primary should count conversation + short-term")
        assertTrue(stats.secondary > 0, "Secondary should count work memory")
        assertTrue(stats.tertiary > 0, "Tertiary should count long-term memory")
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    @Test
    fun `long-term store persists across strategy instances`() {
        val ltFile = File(tempDir, "long-term-memory.json")
        val s1 = LayeredMemoryStrategy(
            longTermStore = LongTermStore(storageFile = ltFile)
        )
        s1.addToLayer(MemoryLayer.LONG_TERM, "persistent knowledge")

        val s2 = LayeredMemoryStrategy(
            longTermStore = LongTermStore(storageFile = ltFile)
        )
        val items = s2.listLayer(MemoryLayer.LONG_TERM)
        assertEquals(1, items.size)
        assertEquals("persistent knowledge", items[0].content)
    }

    @Test
    fun `work and short-term do not persist across instances`() {
        val s1 = strategy()
        s1.addToLayer(MemoryLayer.WORK, "volatile work data")
        s1.addToLayer(MemoryLayer.SHORT_TERM, "volatile note")

        val s2 = strategy() // fresh instance, different in-memory stores
        assertTrue(s2.listLayer(MemoryLayer.WORK).isEmpty())
        assertTrue(s2.listLayer(MemoryLayer.SHORT_TERM).isEmpty())
    }
}
