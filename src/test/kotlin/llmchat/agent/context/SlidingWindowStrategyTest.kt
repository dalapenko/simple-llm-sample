package llmchat.agent.context

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlidingWindowStrategyTest {

    @Test
    fun `addMessage stores exchange in history`() = runBlocking {
        val s = SlidingWindowStrategy(windowSize = 5)
        s.addMessage("hello", "hi there")
        assertEquals(listOf("hello" to "hi there"), s.getMessages())
    }

    @Test
    fun `buildContextBlock is empty when no history`() {
        val s = SlidingWindowStrategy()
        assertTrue(s.buildContextBlock().isEmpty())
    }

    @Test
    fun `buildContextBlock contains user and assistant text`() = runBlocking {
        val s = SlidingWindowStrategy()
        s.addMessage("What is 2+2?", "It is 4.")
        val block = s.buildContextBlock()
        assertContains(block, "What is 2+2?")
        assertContains(block, "It is 4.")
    }

    @Test
    fun `buildContextBlock includes window size info`() = runBlocking {
        val s = SlidingWindowStrategy(windowSize = 5)
        s.addMessage("q", "a")
        val block = s.buildContextBlock()
        assertContains(block, "max 5 exchanges")
    }

    @Test
    fun `clearHistory removes all messages`() = runBlocking {
        val s = SlidingWindowStrategy()
        s.addMessage("q", "a")
        s.clearHistory()
        assertTrue(s.buildContextBlock().isEmpty())
        assertTrue(s.getMessages().isEmpty())
    }

    @Test
    fun `window evicts oldest message when full`() = runBlocking {
        val s = SlidingWindowStrategy(windowSize = 3)
        repeat(5) { i -> s.addMessage("q$i", "a$i") }
        val messages = s.getMessages()
        assertEquals(3, messages.size)
        assertEquals("q2", messages[0].first)
        assertEquals("q4", messages[2].first)
    }

    @Test
    fun `loadMessages replaces history and respects windowSize`() {
        val s = SlidingWindowStrategy(windowSize = 3)
        val history = (0..4).map { i -> "q$i" to "a$i" }
        s.loadMessages(history)
        val messages = s.getMessages()
        // takeLast(3): q2, q3, q4
        assertEquals(3, messages.size)
        assertEquals("q2", messages[0].first)
        assertEquals("q4", messages[2].first)
    }

    @Test
    fun `loadMessages clears previous history`() = runBlocking {
        val s = SlidingWindowStrategy(windowSize = 10)
        s.addMessage("old q", "old a")
        s.loadMessages(listOf("new q" to "new a"))
        assertEquals(listOf("new q" to "new a"), s.getMessages())
    }

    @Test
    fun `estimateTokenStats returns zero for empty strategy`() {
        val stats = SlidingWindowStrategy().estimateTokenStats()
        assertEquals(0, stats.primary)
    }

    @Test
    fun `estimateTokenStats counts tokens for all exchanges`() = runBlocking {
        val s = SlidingWindowStrategy()
        s.addMessage("aaaa", "bbbb") // 1 + 1 = 2 tokens
        val stats = s.estimateTokenStats()
        assertEquals(2, stats.primary)
        assertEquals(0, stats.secondary)
        assertEquals(0, stats.tertiary)
    }

    @Test
    fun `multiple messages accumulate in context block`() = runBlocking {
        val s = SlidingWindowStrategy(windowSize = 10)
        s.addMessage("first question", "first answer")
        s.addMessage("second question", "second answer")
        val block = s.buildContextBlock()
        assertContains(block, "first question")
        assertContains(block, "second answer")
    }

    @Test
    fun `context block does not include evicted messages`() = runBlocking {
        val s = SlidingWindowStrategy(windowSize = 2)
        s.addMessage("evicted", "gone")
        s.addMessage("kept1", "ans1")
        s.addMessage("kept2", "ans2")
        val block = s.buildContextBlock()
        assertFalse(block.contains("evicted"))
        assertContains(block, "kept1")
        assertContains(block, "kept2")
    }
}
