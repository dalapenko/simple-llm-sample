package llmchat.agent.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryLayerTest {

    @Test
    fun `fromCliName returns SHORT_TERM for short-term`() {
        assertEquals(MemoryLayer.SHORT_TERM, MemoryLayer.fromCliName("short-term"))
    }

    @Test
    fun `fromCliName returns WORK for work`() {
        assertEquals(MemoryLayer.WORK, MemoryLayer.fromCliName("work"))
    }

    @Test
    fun `fromCliName returns LONG_TERM for long-term`() {
        assertEquals(MemoryLayer.LONG_TERM, MemoryLayer.fromCliName("long-term"))
    }

    @Test
    fun `fromCliName returns null for unknown name`() {
        assertNull(MemoryLayer.fromCliName("unknown"))
    }

    @Test
    fun `fromCliName is case-sensitive`() {
        assertNull(MemoryLayer.fromCliName("Short-Term"))
        assertNull(MemoryLayer.fromCliName("WORK"))
    }

    @Test
    fun `all entries have distinct cliNames`() {
        val names = MemoryLayer.entries.map { it.cliName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `cliName and displayName are set correctly`() {
        assertEquals("short-term", MemoryLayer.SHORT_TERM.cliName)
        assertEquals("Short-Term", MemoryLayer.SHORT_TERM.displayName)
        assertEquals("work", MemoryLayer.WORK.cliName)
        assertEquals("Work Memory", MemoryLayer.WORK.displayName)
        assertEquals("long-term", MemoryLayer.LONG_TERM.cliName)
        assertEquals("Long-Term", MemoryLayer.LONG_TERM.displayName)
    }
}
