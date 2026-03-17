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
}
