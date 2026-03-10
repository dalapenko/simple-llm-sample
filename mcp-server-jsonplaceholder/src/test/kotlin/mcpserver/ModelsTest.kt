package mcpserver

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Post ─────────────────────────────────────────────────────────────────

    @Test
    fun `Post round-trips through JSON`() {
        val post = Post(userId = 1, id = 42, title = "Test Title", body = "Test body")
        val decoded = json.decodeFromString<Post>(json.encodeToString(post))
        assertEquals(post, decoded)
    }

    @Test
    fun `Post deserializes all fields correctly`() {
        val raw = """{"userId":3,"id":99,"title":"Hello","body":"World"}"""
        val post = json.decodeFromString<Post>(raw)
        assertEquals(3, post.userId)
        assertEquals(99, post.id)
        assertEquals("Hello", post.title)
        assertEquals("World", post.body)
    }

    @Test
    fun `Post deserialization ignores unknown fields`() {
        val raw = """{"userId":1,"id":1,"title":"T","body":"B","extra":"ignored"}"""
        val post = json.decodeFromString<Post>(raw)
        assertEquals(1, post.id)
    }

    @Test
    fun `Post list round-trips through JSON`() {
        val posts = listOf(
            Post(userId = 1, id = 1, title = "First", body = "Body 1"),
            Post(userId = 1, id = 2, title = "Second", body = "Body 2")
        )
        val decoded = json.decodeFromString<List<Post>>(json.encodeToString(posts))
        assertEquals(posts, decoded)
    }

    // ── Comment ───────────────────────────────────────────────────────────────

    @Test
    fun `Comment round-trips through JSON`() {
        val comment = Comment(postId = 5, id = 10, name = "Alice", email = "alice@example.com", body = "Nice!")
        val decoded = json.decodeFromString<Comment>(json.encodeToString(comment))
        assertEquals(comment, decoded)
    }

    @Test
    fun `Comment deserializes all fields correctly`() {
        val raw = """{"postId":5,"id":99,"name":"Bob","email":"bob@test.com","body":"A comment"}"""
        val comment = json.decodeFromString<Comment>(raw)
        assertEquals(5, comment.postId)
        assertEquals(99, comment.id)
        assertEquals("Bob", comment.name)
        assertEquals("bob@test.com", comment.email)
        assertEquals("A comment", comment.body)
    }

    @Test
    fun `Comment list round-trips through JSON`() {
        val comments = listOf(
            Comment(postId = 1, id = 1, name = "A", email = "a@a.com", body = "First"),
            Comment(postId = 1, id = 2, name = "B", email = "b@b.com", body = "Second")
        )
        val decoded = json.decodeFromString<List<Comment>>(json.encodeToString(comments))
        assertEquals(comments, decoded)
    }
}
