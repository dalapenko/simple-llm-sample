package llmchat.agent.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MemoryItemTest {

    @Test
    fun `create sets content correctly`() {
        val item = MemoryItem.create("hello world")
        assertEquals("hello world", item.content)
    }

    @Test
    fun `create sets id to 8 characters`() {
        val item = MemoryItem.create("test")
        assertEquals(8, item.id.length)
    }

    @Test
    fun `create sets createdAt close to current time`() {
        val before = System.currentTimeMillis()
        val item = MemoryItem.create("test")
        val after = System.currentTimeMillis()
        assertTrue(item.createdAt in before..after)
    }

    @Test
    fun `create generates unique ids`() {
        val ids = (1..10).map { MemoryItem.create("content").id }.toSet()
        assertEquals(10, ids.size, "Expected all IDs to be unique")
    }

    @Test
    fun `direct construction preserves all fields`() {
        val item = MemoryItem(id = "abc12345", content = "data", createdAt = 1000L)
        assertEquals("abc12345", item.id)
        assertEquals("data", item.content)
        assertEquals(1000L, item.createdAt)
    }

    @Test
    fun `two items with same id and content are equal`() {
        val a = MemoryItem(id = "abc12345", content = "data", createdAt = 1000L)
        val b = MemoryItem(id = "abc12345", content = "data", createdAt = 1000L)
        assertEquals(a, b)
    }

    @Test
    fun `two items with different id are not equal`() {
        val a = MemoryItem(id = "aaaaaaaa", content = "data", createdAt = 1000L)
        val b = MemoryItem(id = "bbbbbbbb", content = "data", createdAt = 1000L)
        assertNotEquals(a, b)
    }
}
