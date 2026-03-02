package llmchat.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenCounterTest {

    @Test
    fun `blank string returns 0`() {
        assertEquals(0, TokenCounter.estimate(""))
    }

    @Test
    fun `whitespace-only string returns 0`() {
        assertEquals(0, TokenCounter.estimate("   "))
    }

    @Test
    fun `single character returns 1 (minimum floor)`() {
        assertEquals(1, TokenCounter.estimate("a"))
    }

    @Test
    fun `exactly 4 characters returns 1 token`() {
        assertEquals(1, TokenCounter.estimate("abcd"))
    }

    @Test
    fun `8 characters returns 2 tokens`() {
        assertEquals(2, TokenCounter.estimate("abcdefgh"))
    }

    @Test
    fun `token count scales with text length`() {
        val text = "a".repeat(100)
        assertEquals(25, TokenCounter.estimate(text))
    }

    @Test
    fun `3 characters still returns 1 due to floor`() {
        assertEquals(1, TokenCounter.estimate("abc"))
    }
}
