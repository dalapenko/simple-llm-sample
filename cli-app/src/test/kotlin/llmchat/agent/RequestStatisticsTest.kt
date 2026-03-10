package llmchat.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestStatisticsTest {

    @Test
    fun `historyTokens sums window and summary and long-term tokens`() {
        val stats = RequestStatistics(
            response = "reply",
            inputTokens = 10,
            windowTokens = 20,
            summaryTokens = 5,
            responseTokens = 8,
            longTermTokens = 3
        )
        assertEquals(28, stats.historyTokens)
    }

    @Test
    fun `totalTokens sums input and history and response`() {
        val stats = RequestStatistics(
            response = "reply",
            inputTokens = 10,
            windowTokens = 20,
            summaryTokens = 5,
            responseTokens = 8,
            longTermTokens = 3
        )
        assertEquals(46, stats.totalTokens)
    }

    @Test
    fun `historyTokens with default longTermTokens is zero`() {
        val stats = RequestStatistics(
            response = "hi",
            inputTokens = 0,
            windowTokens = 15,
            summaryTokens = 5,
            responseTokens = 0
        )
        assertEquals(20, stats.historyTokens)
    }

    @Test
    fun `all zero values give zero totals`() {
        val stats = RequestStatistics(
            response = "",
            inputTokens = 0,
            windowTokens = 0,
            summaryTokens = 0,
            responseTokens = 0
        )
        assertEquals(0, stats.historyTokens)
        assertEquals(0, stats.totalTokens)
    }
}
