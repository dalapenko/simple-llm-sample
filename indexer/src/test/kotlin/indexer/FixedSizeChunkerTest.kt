package indexer

import indexer.chunker.FixedSizeChunker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixedSizeChunkerTest {

    private val file = File("/fake/doc.txt")

    @Test
    fun `empty content returns no chunks`() {
        val chunks = FixedSizeChunker().chunk("", file)
        assertEquals(0, chunks.size)
    }

    @Test
    fun `blank content returns no chunks`() {
        val chunks = FixedSizeChunker().chunk("   \n\t  ", file)
        assertEquals(0, chunks.size)
    }

    @Test
    fun `content shorter than chunkSize produces a single chunk`() {
        val content = "Hello world"
        val chunks = FixedSizeChunker(chunkSize = 500).chunk(content, file)
        assertEquals(1, chunks.size)
        assertEquals("Hello world", chunks.first().content)
    }

    @Test
    fun `content longer than chunkSize produces multiple chunks`() {
        val content = "A".repeat(1100)
        val chunks = FixedSizeChunker(chunkSize = 500, overlapPct = 0f).chunk(content, file)
        // 1100 / 500 = ceil = 3 chunks (500, 500, 100)
        assertEquals(3, chunks.size)
    }

    @Test
    fun `overlap causes chunks to share characters`() {
        val content = "X".repeat(1000)
        val chunkSize = 500
        val overlapPct = 0.10f
        val overlap = (chunkSize * overlapPct).toInt()  // 50
        val step = chunkSize - overlap                  // 450

        val chunks = FixedSizeChunker(chunkSize, overlapPct).chunk(content, file)

        // First chunk ends at char 500, second starts at char 450 — 50-char overlap
        val expectedChunks = Math.ceil(1000.0 / step).toInt()
        assertTrue(chunks.size in (expectedChunks - 1)..(expectedChunks + 1))
    }

    @Test
    fun `each chunk has correct metadata`() {
        val content = "B".repeat(600)
        val chunks = FixedSizeChunker(chunkSize = 300, overlapPct = 0f).chunk(content, file)
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertEquals("/fake/doc.txt", chunk.metadata.sourcePath)
            assertEquals("doc", chunk.metadata.title)
            assertEquals("fixed", chunk.metadata.strategy)
            assertTrue(chunk.metadata.section?.startsWith("chunk-") == true)
        }
    }

    @Test
    fun `very large file produces reasonable chunk count`() {
        val content = "word ".repeat(10_000) // ~50_000 chars
        val chunks = FixedSizeChunker(chunkSize = 500, overlapPct = 0.12f).chunk(content, file)
        val step = (500 * (1 - 0.12f)).toInt()  // 440
        val expectedApprox = Math.ceil(50_000.0 / step).toInt()
        assertTrue(chunks.size in (expectedApprox - 5)..(expectedApprox + 5))
    }

    @Test
    fun `token estimate is positive for non-empty chunks`() {
        val chunks = FixedSizeChunker().chunk("Hello, this is a test sentence.", file)
        assertTrue(chunks.all { it.tokenEstimate > 0 })
    }
}
