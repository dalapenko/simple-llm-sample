package llmchat.agent.invariant

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertNotEquals

class InvariantStorageTest {

    @TempDir
    lateinit var tempDir: File

    private fun storage() = InvariantStorage(storageFile = File(tempDir, "invariants.json"))

    @Test
    fun `add returns invariant with correct description and category`() {
        val inv = storage().add("use Kotlin only", InvariantCategory.STACK)
        assertEquals("use Kotlin only", inv.description)
        assertEquals(InvariantCategory.STACK, inv.category)
    }

    @Test
    fun `add uses GENERAL category by default`() {
        val inv = storage().add("some rule")
        assertEquals(InvariantCategory.GENERAL, inv.category)
    }

    @Test
    fun `add generates id with INV- prefix`() {
        val inv = storage().add("rule")
        assertTrue(inv.id.startsWith("INV-"), "Expected id to start with 'INV-', got: ${inv.id}")
    }

    @Test
    fun `add generates unique ids`() {
        val s = storage()
        val ids = (1..10).map { s.add("rule $it").id }.toSet()
        assertEquals(10, ids.size)
    }

    @Test
    fun `list returns empty for new storage`() {
        assertTrue(storage().list().isEmpty())
    }

    @Test
    fun `list returns items in insertion order`() {
        val s = storage()
        s.add("first")
        s.add("second")
        s.add("third")
        val items = s.list()
        assertEquals(listOf("first", "second", "third"), items.map { it.description })
    }

    @Test
    fun `list returns snapshot — later adds not reflected`() {
        val s = storage()
        s.add("item")
        val snapshot = s.list()
        s.add("item2")
        // snapshot taken before second add should still have 1 item
        assertEquals(1, snapshot.size)
        assertEquals(2, s.list().size)
    }

    @Test
    fun `remove returns true and deletes item`() {
        val s = storage()
        val inv = s.add("to remove")
        s.add("to keep")

        assertTrue(s.remove(inv.id))
        assertEquals(1, s.list().size)
        assertEquals("to keep", s.list()[0].description)
    }

    @Test
    fun `remove returns false for unknown id`() {
        val s = storage()
        assertFalse(s.remove("INV-FFFFFF"))
    }

    @Test
    fun `remove is case insensitive`() {
        val s = storage()
        val inv = s.add("rule")
        assertTrue(s.remove(inv.id.lowercase()))
        assertTrue(s.list().isEmpty())
    }

    @Test
    fun `clear removes all items`() {
        val s = storage()
        s.add("a")
        s.add("b")
        s.clear()
        assertTrue(s.list().isEmpty())
    }

    @Test
    fun `isEmpty returns true for empty storage`() {
        assertTrue(storage().isEmpty())
    }

    @Test
    fun `isEmpty returns false after adding item`() {
        val s = storage()
        s.add("rule")
        assertFalse(s.isEmpty())
    }

    @Test
    fun `data persists across instances`() {
        val file = File(tempDir, "shared.json")
        InvariantStorage(file).add("persistent rule", InvariantCategory.ARCHITECTURE)

        val loaded = InvariantStorage(file).list()
        assertEquals(1, loaded.size)
        assertEquals("persistent rule", loaded[0].description)
        assertEquals(InvariantCategory.ARCHITECTURE, loaded[0].category)
    }

    @Test
    fun `remove persists across instances`() {
        val file = File(tempDir, "remove-persist.json")
        val s1 = InvariantStorage(file)
        val inv = s1.add("to remove")
        s1.add("to keep")
        s1.remove(inv.id)

        val items = InvariantStorage(file).list()
        assertEquals(1, items.size)
        assertEquals("to keep", items[0].description)
    }

    @Test
    fun `clear persists across instances`() {
        val file = File(tempDir, "clear-persist.json")
        val s1 = InvariantStorage(file)
        s1.add("a")
        s1.add("b")
        s1.clear()

        assertTrue(InvariantStorage(file).list().isEmpty())
    }

    @Test
    fun `estimateTokens returns 0 for empty storage`() {
        assertEquals(0, storage().estimateTokens())
    }

    @Test
    fun `estimateTokens sums description tokens`() {
        val s = storage()
        s.add("aaaa")      // 1 token (4 chars)
        s.add("aaaaaaaa")  // 2 tokens (8 chars)
        assertEquals(3, s.estimateTokens())
    }

    @Test
    fun `buildPromptBlock returns empty string when no invariants`() {
        assertEquals("", storage().buildPromptBlock())
    }

    @Test
    fun `buildPromptBlock contains header and invariant details`() {
        val s = storage()
        s.add("use Kotlin only", InvariantCategory.STACK)

        val block = s.buildPromptBlock()
        assertContains(block, "[PROJECT INVARIANTS — MANDATORY CONSTRAINTS]")
        assertContains(block, "use Kotlin only")
        assertContains(block, "Technology Stack")
        assertContains(block, "INV-")
    }

    @Test
    fun `buildPromptBlock groups by category`() {
        val s = storage()
        s.add("kotlin only", InvariantCategory.STACK)
        s.add("hexagonal arch", InvariantCategory.ARCHITECTURE)

        val block = s.buildPromptBlock()
        assertContains(block, "Technology Stack")
        assertContains(block, "Architecture")
        assertContains(block, "kotlin only")
        assertContains(block, "hexagonal arch")
    }

    @Test
    fun `buildPromptBlock includes violation instructions`() {
        val s = storage()
        s.add("rule", InvariantCategory.GENERAL)

        val block = s.buildPromptBlock()
        assertContains(block, "INVARIANT VIOLATION")
    }

    @Test
    fun `graceful degradation on corrupted file`() {
        val file = File(tempDir, "corrupted.json")
        file.writeText("not valid json }{")

        val s = InvariantStorage(file)
        assertTrue(s.list().isEmpty())
    }

    @Test
    fun `graceful degradation on blank file`() {
        val file = File(tempDir, "blank.json")
        file.writeText("   ")

        val s = InvariantStorage(file)
        assertTrue(s.list().isEmpty())
    }

    @Test
    fun `parent directories are created automatically`() {
        val nestedFile = File(tempDir, "nested/deep/invariants.json")
        val s = InvariantStorage(nestedFile)
        s.add("rule")
        assertTrue(nestedFile.exists())
    }
}
