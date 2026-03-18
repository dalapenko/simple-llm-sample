package llmchat.rag

import indexer.chunker.Chunk
import indexer.chunker.ChunkMetadata
import indexer.store.IndexEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [applyThresholdFilter] — pure filtering logic extracted from
 * [AdvancedRagService]. No network calls required.
 */
class ThresholdFilterTest {

    private fun fakeEntry(id: String): IndexEntry = IndexEntry(
        chunk = Chunk(
            metadata = ChunkMetadata(
                chunkId = id,
                sourcePath = "/fake/$id.kt",
                title = id,
                section = null,
                strategy = "test"
            ),
            content = "content of $id"
        ),
        embedding = FloatArray(4) { 0f }
    )

    private fun scored(id: String, score: Float) = ScoredEntry(fakeEntry(id), score)

    // ── above threshold ───────────────────────────────────────────────────────

    @Test
    fun `all entries above threshold are kept`() {
        val input = listOf(scored("a", 0.9f), scored("b", 0.8f), scored("c", 0.75f))
        val (candidates, filtered) = applyThresholdFilter(input, threshold = 0.65, fallbackK = 3)
        assertEquals(3, candidates.size)
        assertEquals(0, filtered)
    }

    @Test
    fun `entries below threshold are removed`() {
        val input = listOf(scored("a", 0.9f), scored("b", 0.5f), scored("c", 0.4f))
        val (candidates, filtered) = applyThresholdFilter(input, threshold = 0.65, fallbackK = 3)
        assertEquals(1, candidates.size)
        assertEquals("a", candidates[0].entry.chunk.metadata.chunkId)
        assertEquals(2, filtered)
    }

    @Test
    fun `entry exactly at threshold boundary is kept`() {
        val input = listOf(scored("a", 0.65f))
        val (candidates, _) = applyThresholdFilter(input, threshold = 0.65, fallbackK = 3)
        assertEquals(1, candidates.size)
    }

    // ── filteredCount accuracy ────────────────────────────────────────────────

    @Test
    fun `filteredCount equals number of removed entries`() {
        val input = listOf(
            scored("a", 0.9f),
            scored("b", 0.7f),
            scored("c", 0.6f),  // below
            scored("d", 0.5f),  // below
            scored("e", 0.4f),  // below
        )
        val (_, filtered) = applyThresholdFilter(input, threshold = 0.65, fallbackK = 3)
        assertEquals(3, filtered)
    }

    // ── safety fallback when everything is filtered ───────────────────────────

    @Test
    fun `when all entries below threshold fallback returns best fallbackK`() {
        val input = listOf(
            scored("a", 0.3f),
            scored("b", 0.2f),
            scored("c", 0.1f),
            scored("d", 0.05f),
        )
        val (candidates, filtered) = applyThresholdFilter(input, threshold = 0.65, fallbackK = 2)
        assertEquals(2, candidates.size)
        assertEquals(4, filtered)  // all 4 are "filtered" conceptually
        // fallback preserves original order (first fallbackK entries)
        assertEquals("a", candidates[0].entry.chunk.metadata.chunkId)
        assertEquals("b", candidates[1].entry.chunk.metadata.chunkId)
    }

    @Test
    fun `fallback with fallbackK larger than list returns all entries`() {
        val input = listOf(scored("a", 0.3f), scored("b", 0.2f))
        val (candidates, _) = applyThresholdFilter(input, threshold = 0.9, fallbackK = 10)
        assertEquals(2, candidates.size)
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty input returns empty candidates and zero filtered`() {
        val (candidates, filtered) = applyThresholdFilter(emptyList(), threshold = 0.65, fallbackK = 3)
        assertTrue(candidates.isEmpty())
        assertEquals(0, filtered)
    }

    @Test
    fun `threshold 0_0 keeps everything`() {
        val input = listOf(scored("a", 0.1f), scored("b", 0.0f))
        val (candidates, filtered) = applyThresholdFilter(input, threshold = 0.0, fallbackK = 3)
        assertEquals(2, candidates.size)
        assertEquals(0, filtered)
    }

    @Test
    fun `threshold 1_0 removes everything triggering fallback`() {
        val input = listOf(scored("a", 0.99f), scored("b", 0.98f))
        val (candidates, filtered) = applyThresholdFilter(input, threshold = 1.0, fallbackK = 1)
        // no entry has score == 1.0, so fallback kicks in
        assertEquals(1, candidates.size)
        assertEquals(2, filtered)
    }
}
