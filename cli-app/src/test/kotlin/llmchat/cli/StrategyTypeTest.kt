package llmchat.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrategyTypeTest {

    @Test
    fun `fromCliName returns SLIDING_WINDOW`() {
        assertEquals(StrategyType.SLIDING_WINDOW, StrategyType.fromCliName("sliding-window"))
    }

    @Test
    fun `fromCliName returns STICKY_FACTS`() {
        assertEquals(StrategyType.STICKY_FACTS, StrategyType.fromCliName("sticky-facts"))
    }

    @Test
    fun `fromCliName returns BRANCHING`() {
        assertEquals(StrategyType.BRANCHING, StrategyType.fromCliName("branching"))
    }

    @Test
    fun `fromCliName returns LAYERED`() {
        assertEquals(StrategyType.LAYERED, StrategyType.fromCliName("layered"))
    }

    @Test
    fun `fromCliName returns null for unknown name`() {
        assertNull(StrategyType.fromCliName("unknown"))
    }

    @Test
    fun `fromCliName is case-sensitive`() {
        assertNull(StrategyType.fromCliName("Layered"))
        assertNull(StrategyType.fromCliName("BRANCHING"))
    }

    @Test
    fun `default is SLIDING_WINDOW`() {
        assertEquals(StrategyType.SLIDING_WINDOW, StrategyType.default)
    }

    @Test
    fun `availableNames contains all four strategies`() {
        val names = StrategyType.availableNames
        assertTrue(names.contains("sliding-window"))
        assertTrue(names.contains("sticky-facts"))
        assertTrue(names.contains("branching"))
        assertTrue(names.contains("layered"))
        assertEquals(4, names.size)
    }

    @Test
    fun `all entries have distinct cliNames`() {
        val names = StrategyType.entries.map { it.cliName }
        assertEquals(names.size, names.toSet().size)
    }
}
