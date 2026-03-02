package llmchat.agent.memory

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LongTermStoreTest {

    @TempDir
    lateinit var tempDir: File

    private fun store() = LongTermStore(storageFile = File(tempDir, "lt-memory.json"))

    @Test
    fun `add returns item with correct content`() {
        val item = store().add("persistent fact")
        assertEquals("persistent fact", item.content)
    }

    @Test
    fun `list returns items in insertion order`() {
        val s = store()
        s.add("first")
        s.add("second")
        val items = s.list()
        assertEquals(listOf("first", "second"), items.map { it.content })
    }

    @Test
    fun `list returns empty for new file`() {
        assertTrue(store().list().isEmpty())
    }

    @Test
    fun `data persists across instances`() {
        val file = File(tempDir, "shared.json")
        LongTermStore(file).add("knowledge A")
        LongTermStore(file).add("knowledge B")

        val items = LongTermStore(file).list()
        assertEquals(2, items.size)
        assertEquals("knowledge A", items[0].content)
        assertEquals("knowledge B", items[1].content)
    }

    @Test
    fun `delete removes item by id and persists`() {
        val file = File(tempDir, "delete-test.json")
        val s1 = LongTermStore(file)
        val item = s1.add("to remove")
        s1.add("to keep")

        assertTrue(s1.delete(item.id))

        val s2 = LongTermStore(file)
        assertEquals(1, s2.list().size)
        assertEquals("to keep", s2.list()[0].content)
    }

    @Test
    fun `delete returns false for unknown id`() {
        val s = store()
        assertFalse(s.delete("nonexistent"))
    }

    @Test
    fun `clear removes all items and persists`() {
        val file = File(tempDir, "clear-test.json")
        val s1 = LongTermStore(file)
        s1.add("a")
        s1.add("b")
        s1.clear()

        val s2 = LongTermStore(file)
        assertTrue(s2.list().isEmpty())
    }

    @Test
    fun `estimateTokens sums content tokens`() {
        val s = store()
        s.add("aaaa")      // 1 token
        s.add("aaaaaaaa")  // 2 tokens
        assertEquals(3, s.estimateTokens())
    }

    @Test
    fun `estimateTokens returns 0 for empty store`() {
        assertEquals(0, store().estimateTokens())
    }

    @Test
    fun `graceful degradation on corrupted file`() {
        val file = File(tempDir, "corrupted.json")
        file.writeText("this is not valid JSON }{")

        // Should not throw — degrades to empty in-memory store
        val s = LongTermStore(file)
        assertTrue(s.list().isEmpty())
    }

    @Test
    fun `graceful degradation on blank file`() {
        val file = File(tempDir, "blank.json")
        file.writeText("   ")

        val s = LongTermStore(file)
        assertTrue(s.list().isEmpty())
    }

    @Test
    fun `storage file parent directories are created automatically`() {
        val nestedFile = File(tempDir, "nested/deep/memory.json")
        val s = LongTermStore(nestedFile)
        s.add("test")
        assertTrue(nestedFile.exists())
    }
}
