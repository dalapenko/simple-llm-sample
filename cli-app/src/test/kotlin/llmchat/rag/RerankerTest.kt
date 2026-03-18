package llmchat.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [parseRankerResponse] — the pure index-parsing logic extracted
 * from [LlmJudgeReranker]. No HTTP calls; covers all LLM response shapes.
 */
class RerankerTest {

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    fun `clean comma-separated list is parsed correctly`() {
        val result = parseRankerResponse("3,1,7", candidateCount = 10, topK = 3)
        assertEquals(listOf(2, 0, 6), result)   // converted to 0-based
    }

    @Test
    fun `spaces around commas are tolerated`() {
        val result = parseRankerResponse("2, 5, 1", candidateCount = 10, topK = 3)
        assertEquals(listOf(1, 4, 0), result)
    }

    @Test
    fun `newlines and text around numbers are stripped`() {
        val result = parseRankerResponse("Ответ: 4,2,8\n", candidateCount = 10, topK = 3)
        assertEquals(listOf(3, 1, 7), result)
    }

    // ── topK truncation ───────────────────────────────────────────────────────

    @Test
    fun `only topK indices are returned when more are provided`() {
        val result = parseRankerResponse("1,2,3,4,5", candidateCount = 5, topK = 3)
        assertEquals(3, result.size)
        assertEquals(listOf(0, 1, 2), result)
    }

    @Test
    fun `fewer than topK valid indices returns only those`() {
        val result = parseRankerResponse("2,4", candidateCount = 10, topK = 3)
        assertEquals(listOf(1, 3), result)
    }

    // ── deduplication ─────────────────────────────────────────────────────────

    @Test
    fun `duplicate indices are deduplicated keeping first occurrence`() {
        val result = parseRankerResponse("3,1,3,2", candidateCount = 5, topK = 4)
        assertEquals(listOf(2, 0, 1), result)   // 3 appears once, order preserved
    }

    // ── out-of-range filtering ────────────────────────────────────────────────

    @Test
    fun `index 0 is rejected (1-based expected)`() {
        val result = parseRankerResponse("0,1,2", candidateCount = 5, topK = 3)
        assertEquals(listOf(0, 1), result)   // 0 filtered, 1→0, 2→1
    }

    @Test
    fun `index beyond candidateCount is rejected`() {
        val result = parseRankerResponse("1,11,2", candidateCount = 5, topK = 3)
        assertEquals(listOf(0, 1), result)   // 11 filtered
    }

    @Test
    fun `all indices out of range returns empty list (triggers fallback in caller)`() {
        val result = parseRankerResponse("99,100", candidateCount = 5, topK = 3)
        assertTrue(result.isEmpty())
    }

    // ── unparseable responses ─────────────────────────────────────────────────

    @Test
    fun `empty string returns empty list`() {
        assertTrue(parseRankerResponse("", candidateCount = 5, topK = 3).isEmpty())
    }

    @Test
    fun `only text no numbers returns empty list`() {
        assertTrue(parseRankerResponse("нет ответа", candidateCount = 5, topK = 3).isEmpty())
    }

    @Test
    fun `single valid index is accepted`() {
        val result = parseRankerResponse("3", candidateCount = 5, topK = 3)
        assertEquals(listOf(2), result)
    }
}
