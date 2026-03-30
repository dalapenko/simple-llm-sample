package mcpserver

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    private fun toolCall(id: Int, name: String, arguments: String = "{}"): String =
        """{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}"""

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
    fun `initialize returns server name git-mcp`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
        val serverInfo = responses[0].jsonObject["result"]!!.jsonObject["serverInfo"]!!.jsonObject
        assertEquals("git-mcp", serverInfo["name"]?.jsonPrimitive?.content)
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
    fun `tools-list returns exactly five tools`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        val tools = responses[0].jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(5, tools.size)
    }

    @Test
    fun `tools-list contains git_branch`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(toolNames(responses[0]), "git_branch")
    }

    @Test
    fun `tools-list contains git_status`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(toolNames(responses[0]), "git_status")
    }

    @Test
    fun `tools-list contains git_diff`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(toolNames(responses[0]), "git_diff")
    }

    @Test
    fun `tools-list contains git_log`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(toolNames(responses[0]), "git_log")
    }

    @Test
    fun `tools-list contains list_project_files`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertContains(toolNames(responses[0]), "list_project_files")
    }

    @Test
    fun `all tools have a non-blank description`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        val tools = responses[0].jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        for (tool in tools) {
            val desc = tool.jsonObject["description"]?.jsonPrimitive?.content
            assertFalse(desc.isNullOrBlank(), "Tool ${toolName(tool)} has blank description")
        }
    }

    @Test
    fun `all tools declare an inputSchema of type object`() = runTest {
        val responses = runServer("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        val tools = responses[0].jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        for (tool in tools) {
            val schemaType = tool.jsonObject["inputSchema"]!!.jsonObject["type"]?.jsonPrimitive?.content
            assertEquals("object", schemaType, "Tool ${toolName(tool)} inputSchema type is not 'object'")
        }
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
        val responses = runServer(toolCall(1, "nonexistent_tool"))
        assertEquals(-32602, errorCode(responses[0]))
    }

    // ── git_branch ────────────────────────────────────────────────────────────

    @Test
    fun `git_branch returns non-error result`() = runTest {
        val responses = runServer(toolCall(1, "git_branch"))
        assertFalse(isToolError(responses[0]), "git_branch returned isError=true")
    }

    @Test
    fun `git_branch result content has type text`() = runTest {
        val responses = runServer(toolCall(1, "git_branch"))
        assertEquals("text", toolContentType(responses[0]))
    }

    @Test
    fun `git_branch returns non-blank branch name`() = runTest {
        val responses = runServer(toolCall(1, "git_branch"))
        val text = toolText(responses[0])
        assertTrue(text.isNotBlank(), "git_branch returned blank output")
    }

    // ── git_status ────────────────────────────────────────────────────────────

    @Test
    fun `git_status returns non-error result`() = runTest {
        val responses = runServer(toolCall(1, "git_status"))
        assertFalse(isToolError(responses[0]), "git_status returned isError=true")
    }

    @Test
    fun `git_status result content has type text`() = runTest {
        val responses = runServer(toolCall(1, "git_status"))
        assertEquals("text", toolContentType(responses[0]))
    }

    @Test
    fun `git_status returns non-blank output`() = runTest {
        val responses = runServer(toolCall(1, "git_status"))
        val text = toolText(responses[0])
        assertTrue(text.isNotBlank())
    }

    // ── git_diff ──────────────────────────────────────────────────────────────

    @Test
    fun `git_diff returns non-error result`() = runTest {
        val responses = runServer(toolCall(1, "git_diff"))
        assertFalse(isToolError(responses[0]), "git_diff returned isError=true")
    }

    @Test
    fun `git_diff result content has type text`() = runTest {
        val responses = runServer(toolCall(1, "git_diff"))
        assertEquals("text", toolContentType(responses[0]))
    }

    @Test
    fun `git_diff returns non-blank output`() = runTest {
        val responses = runServer(toolCall(1, "git_diff"))
        val text = toolText(responses[0])
        assertTrue(text.isNotBlank())
    }

    // ── git_log ───────────────────────────────────────────────────────────────

    @Test
    fun `git_log returns non-error result with default limit`() = runTest {
        val responses = runServer(toolCall(1, "git_log"))
        assertFalse(isToolError(responses[0]), "git_log returned isError=true")
    }

    @Test
    fun `git_log result content has type text`() = runTest {
        val responses = runServer(toolCall(1, "git_log"))
        assertEquals("text", toolContentType(responses[0]))
    }

    @Test
    fun `git_log respects explicit limit`() = runTest {
        val responses = runServer(toolCall(1, "git_log", """{"limit":3}"""))
        assertFalse(isToolError(responses[0]))
        val lines = toolText(responses[0]).lines().filter { it.isNotBlank() }
        assertTrue(lines.size <= 3, "Expected at most 3 commits, got ${lines.size}")
    }

    @Test
    fun `git_log clamps limit above 50`() = runTest {
        // limit=100 is coerced to 50 — just verify no error and non-blank output
        val responses = runServer(toolCall(1, "git_log", """{"limit":100}"""))
        assertFalse(isToolError(responses[0]))
        assertTrue(toolText(responses[0]).isNotBlank())
    }

    @Test
    fun `git_log clamps limit below 1 to 1`() = runTest {
        // limit=0 is coerced to 1
        val responses = runServer(toolCall(1, "git_log", """{"limit":0}"""))
        assertFalse(isToolError(responses[0]))
        val lines = toolText(responses[0]).lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
    }

    // ── list_project_files ────────────────────────────────────────────────────

    @Test
    fun `list_project_files returns non-error result with no path`() = runTest {
        val responses = runServer(toolCall(1, "list_project_files"))
        assertFalse(isToolError(responses[0]), "list_project_files returned isError=true")
    }

    @Test
    fun `list_project_files result content has type text`() = runTest {
        val responses = runServer(toolCall(1, "list_project_files"))
        assertEquals("text", toolContentType(responses[0]))
    }

    @Test
    fun `list_project_files returns non-blank file list`() = runTest {
        val responses = runServer(toolCall(1, "list_project_files"))
        assertTrue(toolText(responses[0]).isNotBlank())
    }

    @Test
    fun `list_project_files with subdirectory path filters results`() = runTest {
        // Tests run with workdir = mcp-server-git/, so "src" is a valid sub-path
        val responses = runServer(toolCall(1, "list_project_files", """{"path":"src"}"""))
        assertFalse(isToolError(responses[0]))
        val files = toolText(responses[0]).lines().filter { it.isNotBlank() }
        assertTrue(files.isNotEmpty(), "Expected at least one file in src/")
        assertTrue(files.all { it.startsWith("src") }, "Unexpected files outside subdirectory: $files")
    }

    @Test
    fun `list_project_files result is capped at 100 lines`() = runTest {
        val responses = runServer(toolCall(1, "list_project_files"))
        val lines = toolText(responses[0]).lines().filter { it.isNotBlank() }
        assertTrue(lines.size <= 100)
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

    // ── tool result structure ─────────────────────────────────────────────────

    @Test
    fun `successful tool call has result with content array`() = runTest {
        val responses = runServer(toolCall(1, "git_branch"))
        val result = responses[0].jsonObject["result"]!!.jsonObject
        assertTrue(result.containsKey("content"))
        assertTrue(result["content"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun `successful tool call result has isError false`() = runTest {
        val responses = runServer(toolCall(1, "git_branch"))
        val isError = responses[0].jsonObject["result"]!!.jsonObject["isError"]?.jsonPrimitive?.boolean
        assertEquals(false, isError)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun toolNames(response: JsonElement): List<String?> =
        response.jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
            .map { it.jsonObject["name"]?.jsonPrimitive?.content }

    private fun toolName(tool: JsonElement): String? =
        tool.jsonObject["name"]?.jsonPrimitive?.content

    private fun toolText(response: JsonElement): String =
        response.jsonObject["result"]!!.jsonObject["content"]!!.jsonArray
            .first().jsonObject["text"]!!.jsonPrimitive.content

    private fun toolContentType(response: JsonElement): String? =
        response.jsonObject["result"]!!.jsonObject["content"]!!.jsonArray
            .first().jsonObject["type"]?.jsonPrimitive?.content

    private fun isToolError(response: JsonElement): Boolean =
        response.jsonObject["result"]!!.jsonObject["isError"]?.jsonPrimitive?.boolean ?: false

    private fun errorCode(response: JsonElement): Int =
        response.jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.int
}
