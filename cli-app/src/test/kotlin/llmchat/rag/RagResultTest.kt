package llmchat.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [RagResult] field defaults and [RagPipeline] contract expectations.
 */
class RagResultTest {

    // ── defaults ──────────────────────────────────────────────────────────────

    @Test
    fun `basic constructor sets all fields`() {
        val result = RagResult(
            augmentedMessage = "prompt",
            sources = listOf("File.kt"),
            chunksFound = 3
        )
        assertEquals("prompt", result.augmentedMessage)
        assertEquals(listOf("File.kt"), result.sources)
        assertEquals(3, result.chunksFound)
        assertNull(result.rewrittenQuery)
        assertEquals(3, result.initialChunksFound)   // defaults to chunksFound
        assertEquals(0, result.filteredCount)
    }

    @Test
    fun `rewrittenQuery is null for basic RAG result`() {
        val result = RagResult("q", emptyList(), 0)
        assertNull(result.rewrittenQuery)
    }

    @Test
    fun `initialChunksFound defaults to chunksFound`() {
        val result = RagResult("q", emptyList(), 5)
        assertEquals(5, result.initialChunksFound)
    }

    // ── advanced RAG result ───────────────────────────────────────────────────

    @Test
    fun `advanced result carries rewrittenQuery and filter stats`() {
        val result = RagResult(
            augmentedMessage = "ctx + question",
            sources = listOf("A.kt", "B.kt"),
            chunksFound = 3,
            rewrittenQuery = "expanded search query",
            initialChunksFound = 10,
            filteredCount = 4
        )
        assertEquals("expanded search query", result.rewrittenQuery)
        assertEquals(10, result.initialChunksFound)
        assertEquals(4, result.filteredCount)
        assertEquals(3, result.chunksFound)
    }

    @Test
    fun `filteredCount plus chunksFound can be less than initialChunksFound (reranker reduced further)`() {
        // 10 initial, 4 filtered by threshold → 6 remain, reranker picks 3 final
        val result = RagResult(
            augmentedMessage = "prompt",
            sources = listOf("A.kt", "B.kt", "C.kt"),
            chunksFound = 3,
            initialChunksFound = 10,
            filteredCount = 4
        )
        // 3 (final) + 4 (filtered) = 7, not 10 — the remaining 3 were dropped by reranker
        assertEquals(7, result.chunksFound + result.filteredCount)
    }

    // ── empty result (no matches) ─────────────────────────────────────────────

    @Test
    fun `empty result has zero chunks and empty sources`() {
        val result = RagResult(augmentedMessage = "original query", sources = emptyList(), chunksFound = 0)
        assertEquals(0, result.chunksFound)
        assertEquals(emptyList<String>(), result.sources)
        assertEquals(0, result.filteredCount)
    }

    // ── lowRelevance / topScore / chunkIds (Trustworthy RAG) ──────────────────

    @Test
    fun `lowRelevance defaults to false`() {
        val result = RagResult("msg", emptyList(), 0)
        assertEquals(false, result.lowRelevance)
    }

    @Test
    fun `topScore defaults to zero`() {
        val result = RagResult("msg", emptyList(), 0)
        assertEquals(0f, result.topScore)
    }

    @Test
    fun `chunkIds defaults to empty list`() {
        val result = RagResult("msg", emptyList(), 0)
        assertEquals(emptyList<String>(), result.chunkIds)
    }

    @Test
    fun `low relevance result sets flag and message`() {
        val result = RagResult(
            augmentedMessage = "Я не знаю ответа на этот вопрос",
            sources = emptyList(),
            chunksFound = 0,
            lowRelevance = true,
            topScore = 0.30f
        )
        assertEquals(true, result.lowRelevance)
        assertEquals(0.30f, result.topScore)
        assertEquals(emptyList<String>(), result.chunkIds)
    }

    @Test
    fun `successful result carries chunkIds aligned with sources`() {
        val result = RagResult(
            augmentedMessage = "context + question",
            sources = listOf("Foo.kt (bar)", "Baz.kt"),
            chunksFound = 2,
            topScore = 0.72f,
            chunkIds = listOf("a1b2c3d4", "e5f6a7b8")
        )
        assertEquals(false, result.lowRelevance)
        assertEquals(0.72f, result.topScore)
        assertEquals(listOf("a1b2c3d4", "e5f6a7b8"), result.chunkIds)
        assertEquals(2, result.chunkIds.size)
    }

    @Test
    fun `chunkIds count matches sources count in well-formed result`() {
        val result = RagResult(
            augmentedMessage = "prompt",
            sources = listOf("A.kt", "B.kt", "C.kt"),
            chunksFound = 3,
            chunkIds = listOf("00000001", "00000002", "00000003")
        )
        assertEquals(result.sources.size, result.chunkIds.size)
    }
}
