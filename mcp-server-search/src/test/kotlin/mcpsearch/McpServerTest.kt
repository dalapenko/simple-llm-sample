package mcpsearch

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpServerTest {

    // ── Test infrastructure ───────────────────────────────────────────────────

    private class FakeSearchClient(
        private val response: JsonElement = buildJsonObject {
            put("query", "test")
            put("items", buildJsonArray {})
            put("totalItems", 0)
        }
    ) : SearchClient {
        var lastQuery: String? = null
        var lastLimit: Int? = null

        override suspend fun search(query: String, limit: Int): JsonElement {
            lastQuery = query
            lastLimit = limit
            return response
        }

        override fun close() {}
    }

    private fun send(
        vararg requests: String,
        client: SearchClient = FakeSearchClient()
    ): List<JsonObject> {
        val inputBytes = requests.joinToString("\n", postfix = "\n").toByteArray()
        val output = ByteArrayOutputStream()
        val server = McpServer(ByteArrayInputStream(inputBytes), output, client)
        runBlocking { server.run() }
        return output.toString()
            .lines()
            .filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }
    }

    private fun rpc(id: Int, method: String, params: String = "null") =
        """{"jsonrpc":"2.0","id":$id,"method":"$method","params":$params}"""

    private fun toolCall(id: Int, tool: String, args: String = "{}") =
        rpc(id, "tools/call", """{"name":"$tool","arguments":$args}""")

    private fun toolText(response: JsonObject): String =
        response["result"]!!.jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

    private fun isToolError(response: JsonObject): Boolean =
        response["result"]!!.jsonObject["isError"]!!.jsonPrimitive.boolean

    // ── initialize ────────────────────────────────────────────────────────────

    @Test
    fun `initialize returns protocol version 2024-11-05`() {
        val result = send(rpc(1, "initialize")).first()["result"]!!.jsonObject
        assertEquals("2024-11-05", result["protocolVersion"]!!.jsonPrimitive.content)
    }

    @Test
    fun `initialize returns server name search-mcp`() {
        val result = send(rpc(1, "initialize")).first()["result"]!!.jsonObject
        assertEquals("search-mcp", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `initialize returns capabilities`() {
        val result = send(rpc(1, "initialize")).first()["result"]!!.jsonObject
        assertNotNull(result["capabilities"])
    }

    // ── ping ──────────────────────────────────────────────────────────────────

    @Test
    fun `ping returns result without error`() {
        val response = send(rpc(1, "ping")).first()
        assertNotNull(response["result"])
        assertNull(response["error"])
    }

    // ── tools/list ────────────────────────────────────────────────────────────

    @Test
    fun `tools list returns exactly 1 tool`() {
        val tools = send(rpc(1, "tools/list")).first()["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(1, tools.size)
    }

    @Test
    fun `tools list contains search tool`() {
        val tools = send(rpc(1, "tools/list")).first()["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals("search", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    // ── unknown method ────────────────────────────────────────────────────────

    @Test
    fun `unknown method returns -32601 error`() {
        val response = send(rpc(1, "unknown/method")).first()
        assertNotNull(response["error"])
        assertEquals(-32601, response["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    // ── notification methods are silently ignored ─────────────────────────────

    @Test
    fun `notification initialized produces no response`() {
        val responses = send(rpc(1, "notifications/initialized"), rpc(2, "ping"))
        assertEquals(1, responses.size) // only ping responded
    }

    @Test
    fun `notification cancelled produces no response`() {
        val responses = send(rpc(1, "notifications/cancelled"), rpc(2, "ping"))
        assertEquals(1, responses.size)
    }

    // ── parse error ───────────────────────────────────────────────────────────

    @Test
    fun `malformed JSON returns -32700 parse error`() {
        val response = send("not valid json { at all").first()
        assertEquals(-32700, response["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    // ── response ID matches request ID ────────────────────────────────────────

    @Test
    fun `response id matches request id`() {
        val responses = send(rpc(42, "ping"), rpc(99, "tools/list"))
        assertEquals(42, responses[0]["id"]!!.jsonPrimitive.int)
        assertEquals(99, responses[1]["id"]!!.jsonPrimitive.int)
    }

    @Test
    fun `multiple requests processed in order`() {
        val responses = send(rpc(1, "ping"), rpc(2, "initialize"), rpc(3, "tools/list"))
        assertEquals(3, responses.size)
    }

    // ── unknown tool call ─────────────────────────────────────────────────────

    @Test
    fun `calling unknown tool returns error`() {
        val response = send(toolCall(1, "nonexistent_tool")).first()
        assertNotNull(response["error"])
    }

    // ── search tool: input validation ─────────────────────────────────────────

    @Test
    fun `search without query returns error`() {
        val response = send(toolCall(1, "search", "{}")).first()
        assertTrue(response["error"] != null || isToolError(response))
    }

    @Test
    fun `search with blank query returns error`() {
        val response = send(toolCall(1, "search", """{"query":"   "}""")).first()
        assertTrue(response["error"] != null || isToolError(response))
    }

    @Test
    fun `search with query exceeding 500 chars returns error`() {
        val longQuery = "q".repeat(501)
        val response = send(toolCall(1, "search", """{"query":"$longQuery"}""")).first()
        assertTrue(response["error"] != null || isToolError(response))
    }

    @Test
    fun `search with limit 0 returns error`() {
        val response = send(toolCall(1, "search", """{"query":"kotlin","limit":0}""")).first()
        assertTrue(response["error"] != null || isToolError(response))
    }

    @Test
    fun `search with limit 21 returns error`() {
        val response = send(toolCall(1, "search", """{"query":"kotlin","limit":21}""")).first()
        assertTrue(response["error"] != null || isToolError(response))
    }

    @Test
    fun `search with limit 1 is accepted`() {
        val fake = FakeSearchClient()
        send(toolCall(1, "search", """{"query":"kotlin","limit":1}"""), client = fake)
        assertEquals(1, fake.lastLimit)
    }

    @Test
    fun `search with limit 20 is accepted`() {
        val fake = FakeSearchClient()
        send(toolCall(1, "search", """{"query":"kotlin","limit":20}"""), client = fake)
        assertEquals(20, fake.lastLimit)
    }

    // ── search tool: client delegation ────────────────────────────────────────

    @Test
    fun `search passes query to client`() {
        val fake = FakeSearchClient()
        send(toolCall(1, "search", """{"query":"Kotlin coroutines"}"""), client = fake)
        assertEquals("Kotlin coroutines", fake.lastQuery)
    }

    @Test
    fun `search uses default limit 5 when not specified`() {
        val fake = FakeSearchClient()
        send(toolCall(1, "search", """{"query":"kotlin"}"""), client = fake)
        assertEquals(5, fake.lastLimit)
    }

    @Test
    fun `search passes custom limit to client`() {
        val fake = FakeSearchClient()
        send(toolCall(1, "search", """{"query":"kotlin","limit":10}"""), client = fake)
        assertEquals(10, fake.lastLimit)
    }

    // ── search tool: response shape ───────────────────────────────────────────

    @Test
    fun `search wraps client result under searchResults key`() {
        val fakeResult = buildJsonObject {
            put("query", "kotlin")
            put("items", buildJsonArray {})
            put("totalItems", 0)
        }
        val responses = send(
            toolCall(1, "search", """{"query":"kotlin"}"""),
            client = FakeSearchClient(fakeResult)
        )
        val text = toolText(responses[0])
        val result = Json.parseToJsonElement(text).jsonObject
        assertNotNull(result["searchResults"])
    }

    @Test
    fun `successful search result is not an error`() {
        val responses = send(toolCall(1, "search", """{"query":"kotlin"}"""))
        assertNotNull(responses[0]["result"])
        val isError = responses[0]["result"]!!.jsonObject["isError"]!!.jsonPrimitive.boolean
        assertTrue(!isError)
    }

    @Test
    fun `search result contains client response data`() {
        val fakeResult = buildJsonObject {
            put("query", "jvm")
            put("items", buildJsonArray {
                add(buildJsonObject {
                    put("title", "JVM")
                    put("excerpt", "Java Virtual Machine")
                    put("url", "https://example.com")
                    put("relevanceScore", 1.0)
                })
            })
            put("totalItems", 1)
        }
        val responses = send(
            toolCall(1, "search", """{"query":"jvm"}"""),
            client = FakeSearchClient(fakeResult)
        )
        val text = toolText(responses[0])
        val searchResults = Json.parseToJsonElement(text).jsonObject["searchResults"]!!.jsonObject
        assertEquals(1, searchResults["totalItems"]!!.jsonPrimitive.int)
        assertEquals("JVM", searchResults["items"]!!.jsonArray[0].jsonObject["title"]!!.jsonPrimitive.content)
    }
}
