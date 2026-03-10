package llmchat.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContextWindowConfigTest {

    @Test
    fun `default values are valid`() {
        val config = ContextWindowConfig()
        assertEquals(10, config.windowSize)
        assertEquals(10, config.summaryBatchSize)
    }

    @Test
    fun `custom valid values are accepted`() {
        val config = ContextWindowConfig(windowSize = 5, summaryBatchSize = 20)
        assertEquals(5, config.windowSize)
        assertEquals(20, config.summaryBatchSize)
    }

    @Test
    fun `windowSize of 1 is valid (minimum)`() {
        val config = ContextWindowConfig(windowSize = 1)
        assertEquals(1, config.windowSize)
    }

    @Test
    fun `windowSize of 0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            ContextWindowConfig(windowSize = 0)
        }
    }

    @Test
    fun `negative windowSize throws`() {
        assertFailsWith<IllegalArgumentException> {
            ContextWindowConfig(windowSize = -5)
        }
    }

    @Test
    fun `summaryBatchSize of 1 is valid (minimum)`() {
        val config = ContextWindowConfig(summaryBatchSize = 1)
        assertEquals(1, config.summaryBatchSize)
    }

    @Test
    fun `summaryBatchSize of 0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            ContextWindowConfig(summaryBatchSize = 0)
        }
    }

    @Test
    fun `negative summaryBatchSize throws`() {
        assertFailsWith<IllegalArgumentException> {
            ContextWindowConfig(summaryBatchSize = -1)
        }
    }
}
