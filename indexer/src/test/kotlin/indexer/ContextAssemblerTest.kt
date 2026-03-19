package indexer

import indexer.chunker.Chunk
import indexer.chunker.ChunkMetadata
import indexer.search.ContextAssembler
import indexer.store.IndexEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextAssemblerTest {

    private fun entry(sourcePath: String, section: String?, content: String): IndexEntry {
        val meta = ChunkMetadata(
            sourcePath = sourcePath,
            title = sourcePath.substringAfterLast("/").substringBeforeLast("."),
            section = section,
            strategy = "structural"
        )
        return IndexEntry(chunk = Chunk(meta, content), embedding = FloatArray(0))
    }

    @Test
    fun `empty list returns empty string`() {
        assertEquals("", ContextAssembler.assemble(emptyList()))
    }

    @Test
    fun `single entry contains filename and content`() {
        val result = ContextAssembler.assemble(listOf(
            entry("/project/Foo.kt", "Bar", "fun bar() = 42")
        ))
        assertTrue("Foo.kt" in result)
        assertTrue("Bar" in result)
        assertTrue("fun bar() = 42" in result)
    }

    @Test
    fun `entry without section omits section label`() {
        val result = ContextAssembler.assemble(listOf(
            entry("/project/Foo.kt", null, "content")
        ))
        assertTrue("Раздел" !in result)
    }

    @Test
    fun `entry with section includes section label`() {
        val result = ContextAssembler.assemble(listOf(
            entry("/project/Foo.kt", "myFunction", "content")
        ))
        assertTrue("Раздел: myFunction" in result)
    }

    @Test
    fun `multiple entries are separated by divider`() {
        val result = ContextAssembler.assemble(listOf(
            entry("/a/First.kt", null, "first"),
            entry("/b/Second.kt", null, "second")
        ))
        assertTrue("---" in result)
        assertTrue("First.kt" in result)
        assertTrue("Second.kt" in result)
    }

    @Test
    fun `only filename is used not full path`() {
        val result = ContextAssembler.assemble(listOf(
            entry("/very/deep/path/Target.kt", null, "body")
        ))
        assertTrue("Target.kt" in result)
        assertTrue("/very/deep/path" !in result)
    }

    @Test
    fun `summarizeSources returns filename and section`() {
        val sources = ContextAssembler.summarizeSources(listOf(
            entry("/project/Foo.kt", "myFun", "body"),
            entry("/project/Bar.kt", null, "body")
        ))
        assertEquals(2, sources.size)
        assertEquals("Foo.kt (myFun)", sources[0])
        assertEquals("Bar.kt", sources[1])
    }

    @Test
    fun `summarizeSources empty list returns empty`() {
        assertEquals(emptyList(), ContextAssembler.summarizeSources(emptyList()))
    }

    // ── assembleWithIds ───────────────────────────────────────────────────────

    @Test
    fun `assembleWithIds empty list returns empty string`() {
        assertEquals("", ContextAssembler.assembleWithIds(emptyList()))
    }

    @Test
    fun `assembleWithIds prefixes each block with short chunk ID`() {
        val e = entry("/project/Foo.kt", null, "body")
        val result = ContextAssembler.assembleWithIds(listOf(e))
        val shortId = e.chunk.metadata.chunkId.take(8)
        assertTrue("[ID: $shortId]" in result, "Expected [ID: $shortId] in output")
    }

    @Test
    fun `assembleWithIds short ID is exactly 8 chars`() {
        val e = entry("/project/Foo.kt", null, "body")
        val result = ContextAssembler.assembleWithIds(listOf(e))
        val idMatch = Regex("""\[ID: ([0-9a-f-]{8})\]""").find(result)
        assertEquals(8, idMatch?.groupValues?.get(1)?.length)
    }

    @Test
    fun `assembleWithIds still includes filename and content`() {
        val result = ContextAssembler.assembleWithIds(listOf(
            entry("/project/Bar.kt", "myFun", "fun myFun() = 1")
        ))
        assertTrue("Bar.kt" in result)
        assertTrue("myFun" in result)
        assertTrue("fun myFun() = 1" in result)
    }

    @Test
    fun `assembleWithIds multiple entries are separated by divider`() {
        val result = ContextAssembler.assembleWithIds(listOf(
            entry("/a/First.kt", null, "first"),
            entry("/b/Second.kt", null, "second")
        ))
        assertTrue("---" in result)
        val idMatches = Regex("""\[ID: [0-9a-f-]{8}\]""").findAll(result).count()
        assertEquals(2, idMatches)
    }

    // ── extractChunkIds ───────────────────────────────────────────────────────

    @Test
    fun `extractChunkIds empty list returns empty`() {
        assertEquals(emptyList(), ContextAssembler.extractChunkIds(emptyList()))
    }

    @Test
    fun `extractChunkIds returns short IDs in entry order`() {
        val e1 = entry("/a/A.kt", null, "a")
        val e2 = entry("/b/B.kt", null, "b")
        val ids = ContextAssembler.extractChunkIds(listOf(e1, e2))
        assertEquals(2, ids.size)
        assertEquals(e1.chunk.metadata.chunkId.take(8), ids[0])
        assertEquals(e2.chunk.metadata.chunkId.take(8), ids[1])
    }

    @Test
    fun `extractChunkIds each ID is 8 chars`() {
        val ids = ContextAssembler.extractChunkIds(listOf(
            entry("/a/A.kt", null, "body"),
            entry("/b/B.kt", null, "body")
        ))
        ids.forEach { assertEquals(8, it.length) }
    }
}
