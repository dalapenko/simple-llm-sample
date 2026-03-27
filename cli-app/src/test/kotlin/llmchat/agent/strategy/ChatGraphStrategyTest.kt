package llmchat.agent.strategy

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class ChatGraphStrategyTest {

    @Test
    fun `chatSingleRunGraphStrategy returns non-null strategy`() {
        val strategy = chatSingleRunGraphStrategy()
        assertNotNull(strategy)
    }

    @Test
    fun `chatSingleRunGraphStrategy has correct name`() {
        val strategy = chatSingleRunGraphStrategy()
        assertEquals("chat_single_run", strategy.name)
    }

    @Test
    fun `chatSingleRunGraphStrategy creates independent instances`() {
        val s1 = chatSingleRunGraphStrategy()
        val s2 = chatSingleRunGraphStrategy()
        // Each call produces an independent object — no shared mutable state
        assertNotSame(s1, s2)
    }

    @Test
    fun `chatSingleRunGraphStrategy returns AIAgentGraphStrategy type`() {
        val strategy = chatSingleRunGraphStrategy()
        assertIs<AIAgentGraphStrategy<String, String>>(strategy)
    }

    @Test
    fun `chatSingleRunGraphStrategy can be called multiple times without exceptions`() {
        // Verifies the DSL builder is safe to invoke repeatedly (fresh agent per turn pattern)
        repeat(3) { chatSingleRunGraphStrategy() }
    }
}
