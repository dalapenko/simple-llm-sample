package mcpserver

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import kotlin.test.*

class McpServerTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Feeds [messages] line-by-line into [McpServer] via a [ByteArrayInputStream]
     * and returns the parsed JSON-RPC response objects written to stdout.
     *
     * The server loop terminates naturally when the input stream reaches EOF.
     */
    private suspend fun runServer(vararg messages: String): List<JsonElement> {
        val input = messages.joinToString("\n").byteInputStream()
        val output = ByteArrayOutputStream()
        McpServer(input, output).run()
        return output.toString()
            .lines()
            .filter { it.isNotBlank() }
            .map { json.parseToJsonElement(it) }
    }

    // ── initialize ────────────────────────────────────────────────────────────

    @Test
    fun `initialize response has jsonrpc 2-0`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
        assertEquals("2.0", responses[0].jsonObject["jsonrpc"]?.jsonPrimitive?.content)
    }

    @Test
    fun `initialize response echoes the request id`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":42,"method":"initialize","params":{}}""")
        assertEquals(42, responses[0].jsonObject["id"]?.jsonPrimitive?.int)
    }

    @Test
    fun `initialize returns protocol version 2024-11-05`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
        val result = responses[0].jsonObject["result"]!!.jsonObject
        assertEquals("2024-11-05", result["protocolVersion"]?.jsonPrimitive?.content)
    }

    @Test
    fun `initialize declares tools capability`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
        val capabilities = responses[0].jsonObject["result"]!!.jsonObject["capabilities"]!!.jsonObject
        assertTrue(capabilities.containsKey("tools"))
    }

    @Test
    fun `initialize returns server name jsonplaceholder-mcp`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
        val serverInfo = responses[0].jsonObject["result"]!!.jsonObject["serverInfo"]!!.jsonObject
        assertEquals("jsonplaceholder-mcp", serverInfo["name"]?.jsonPrimitive?.content)
    }

    // ── notifications ─────────────────────────────────────────────────────────

    @Test
    fun `notifications-initialized produces no response`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertTrue(responses.isEmpty())
    }

    @Test
    fun `notification does not block processing of the next request`() = runTest {
        val responses = runServer(
            """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
            """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
        )
        assertEquals(1, responses.size)
        assertNotNull(responses[0].jsonObject["result"])
    }

    // ── tools/list ────────────────────────────────────────────────────────────

    @Test
    fun `tools-list returns exactly three tools`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        val tools = responses[0].jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(3, tools.size)
    }

    @Test
    fun `tools-list contains get_posts`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(toolNames(responses[0]), "get_posts")
    }

    @Test
    fun `tools-list contains get_post_details`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(toolNames(responses[0]), "get_post_details")
    }

    @Test
    fun `tools-list contains get_comments`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(toolNames(responses[0]), "get_comments")
    }

    @Test
    fun `get_post_details declares postId as required`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(requiredParams(responses[0], "get_post_details"), "postId")
    }

    @Test
    fun `get_comments declares postId as required`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(requiredParams(responses[0], "get_comments"), "postId")
    }

    @Test
    fun `get_posts has no required parameters`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        val tools = responses[0].jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        val tool = tools.first { it.jsonObject["name"]?.jsonPrimitive?.content == "get_posts" }
        assertFalse(tool.jsonObject["inputSchema"]!!.jsonObject.containsKey("required"))
    }

    // ── ping ──────────────────────────────────────────────────────────────────

    @Test
    fun `ping returns empty result object`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        assertTrue(responses[0].jsonObject["result"]!!.jsonObject.isEmpty())
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    fun `unknown method returns error -32601`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"unknown/method"}""")
        assertEquals(-32601, errorCode(responses[0]))
    }

    @Test
    fun `error response echoes the request id`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":77,"method":"unknown"}""")
        assertEquals(77, responses[0].jsonObject["id"]?.jsonPrimitive?.int)
    }

    @Test
    fun `invalid JSON returns error -32700`() = runTest {
        val responses = runServer("not valid json at all")
        assertEquals(-32700, errorCode(responses[0]))
    }

    @Test
    fun `tools-call without name returns error -32602`() = runTest {
        val responses = runServer(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"arguments":{}}}"""
        )
        assertEquals(-32602, errorCode(responses[0]))
    }

    @Test
    fun `tools-call with unknown tool name returns error -32602`() = runTest {
        val responses = runServer(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"nonexistent","arguments":{}}}"""
        )
        assertEquals(-32602, errorCode(responses[0]))
    }

    @Test
    fun `get_post_details without postId argument returns error -32602`() = runTest {
        val responses = runServer(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_post_details","arguments":{}}}"""
        )
        assertEquals(-32602, errorCode(responses[0]))
    }

    @Test
    fun `get_comments without postId argument returns error -32602`() = runTest {
        val responses = runServer(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_comments","arguments":{}}}"""
        )
        assertEquals(-32602, errorCode(responses[0]))
    }

    // ── sequential processing ─────────────────────────────────────────────────

    @Test
    fun `multiple requests are processed in sequence with correct ids`() = runTest {
        val responses = runServer(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""",
            """{"jsonrpc":"2.0","id":3,"method":"ping"}"""
        )
        assertEquals(3, responses.size)
        assertEquals(1, responses[0].jsonObject["id"]?.jsonPrimitive?.int)
        assertEquals(2, responses[1].jsonObject["id"]?.jsonPrimitive?.int)
        assertEquals(3, responses[2].jsonObject["id"]?.jsonPrimitive?.int)
    }

    @Test
    fun `error in one request does not stop processing of subsequent requests`() = runTest {
        val responses = runServer(
            """{"jsonrpc":"2.0","id":1,"method":"unknown"}""",
            """{"jsonrpc":"2.0","id":2,"method":"ping"}"""
        )
        assertEquals(2, responses.size)
        assertEquals(-32601, errorCode(responses[0]))
        assertNotNull(responses[1].jsonObject["result"])
    }

    @Test
    fun `blank lines between requests are ignored`() = runTest {
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"ping"}""",
            "",
            "   ",
            """{"jsonrpc":"2.0","id":2,"method":"ping"}"""
        )
        val output = ByteArrayOutputStream()
        McpServer(input.joinToString("\n").byteInputStream(), output).run()
        val responses = output.toString().lines().filter { it.isNotBlank() }
        assertEquals(2, responses.size)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun toolNames(response: JsonElement): List<String?> =
        response.jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
            .map { it.jsonObject["name"]?.jsonPrimitive?.content }

    private fun requiredParams(response: JsonElement, toolName: String): List<String> {
        val tools = response.jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        val tool = tools.first { it.jsonObject["name"]?.jsonPrimitive?.content == toolName }
        return tool.jsonObject["inputSchema"]!!.jsonObject["required"]!!.jsonArray
            .map { it.jsonPrimitive.content }
    }

    private fun errorCode(response: JsonElement): Int =
        response.jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.int
}
