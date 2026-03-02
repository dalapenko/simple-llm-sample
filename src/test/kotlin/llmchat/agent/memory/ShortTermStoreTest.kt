package llmchat.agent.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortTermStoreTest {

    @Test
    fun `add returns the created item with correct content`() {
        val store = ShortTermStore()
        val item = store.add("hello")
        assertEquals("hello", item.content)
    }

    @Test
    fun `list returns items in insertion order`() {
        val store = ShortTermStore()
        store.add("first")
        store.add("second")
        store.add("third")
        val items = store.list()
        assertEquals(listOf("first", "second", "third"), items.map { it.content })
    }

    @Test
    fun `list returns empty list when store is empty`() {
        val store = ShortTermStore()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `delete removes item by id and returns true`() {
        val store = ShortTermStore()
        val item = store.add("to delete")
        store.add("to keep")

        assertTrue(store.delete(item.id))
        assertEquals(1, store.list().size)
        assertEquals("to keep", store.list()[0].content)
    }

    @Test
    fun `delete returns false for unknown id`() {
        val store = ShortTermStore()
        assertFalse(store.delete("nonexistent"))
    }

    @Test
    fun `clear removes all items`() {
        val store = ShortTermStore()
        store.add("a")
        store.add("b")
        store.clear()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `sliding window evicts oldest when maxItems exceeded`() {
        val store = ShortTermStore(maxItems = 3)
        repeat(5) { i -> store.add("item$i") }
        val items = store.list()
        assertEquals(3, items.size)
        assertEquals("item2", items[0].content)
        assertEquals("item3", items[1].content)
        assertEquals("item4", items[2].content)
    }

    @Test
    fun `maxItems of 1 keeps only last item`() {
        val store = ShortTermStore(maxItems = 1)
        store.add("first")
        store.add("second")
        val items = store.list()
        assertEquals(1, items.size)
        assertEquals("second", items[0].content)
    }

    @Test
    fun `estimateTokens sums tokens for all items`() {
        val store = ShortTermStore()
        store.add("aaaa")     // 1 token
        store.add("aaaaaaaa") // 2 tokens
        assertEquals(3, store.estimateTokens())
    }

    @Test
    fun `estimateTokens returns 0 for empty store`() {
        val store = ShortTermStore()
        assertEquals(0, store.estimateTokens())
    }
}
