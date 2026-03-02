package llmchat.agent.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkMemoryStoreTest {

    @Test
    fun `add returns the created item with correct content`() {
        val store = WorkMemoryStore()
        val item = store.add("task context")
        assertEquals("task context", item.content)
    }

    @Test
    fun `list returns items in insertion order`() {
        val store = WorkMemoryStore()
        store.add("first")
        store.add("second")
        val items = store.list()
        assertEquals(listOf("first", "second"), items.map { it.content })
    }

    @Test
    fun `list returns empty list when store is empty`() {
        val store = WorkMemoryStore()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `delete removes item by id and returns true`() {
        val store = WorkMemoryStore()
        val item = store.add("to delete")
        store.add("to keep")

        assertTrue(store.delete(item.id))
        assertEquals(1, store.list().size)
        assertEquals("to keep", store.list()[0].content)
    }

    @Test
    fun `delete returns false for unknown id`() {
        val store = WorkMemoryStore()
        assertFalse(store.delete("does-not-exist"))
    }

    @Test
    fun `clear removes all items`() {
        val store = WorkMemoryStore()
        store.add("a")
        store.add("b")
        store.add("c")
        store.clear()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `no item limit — accumulates all added items`() {
        val store = WorkMemoryStore()
        repeat(100) { i -> store.add("item$i") }
        assertEquals(100, store.list().size)
    }

    @Test
    fun `estimateTokens sums tokens for all items`() {
        val store = WorkMemoryStore()
        store.add("aaaa")     // 1 token
        store.add("aaaaaaaa") // 2 tokens
        assertEquals(3, store.estimateTokens())
    }

    @Test
    fun `estimateTokens returns 0 for empty store`() {
        val store = WorkMemoryStore()
        assertEquals(0, store.estimateTokens())
    }
}
