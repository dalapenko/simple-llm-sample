package mcpscheduler

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpServerTest {

    private lateinit var tempFile: File

    @BeforeTest
    fun setup() {
        tempFile = Files.createTempFile("reminders-mcp-test", ".json").toFile()
        tempFile.delete()
    }

    @AfterTest
    fun teardown() {
        tempFile.delete()
    }

    // ── Test infrastructure ───────────────────────────────────────────────────

    /**
     * Runs [requests] through a McpServer backed by a fresh [ReminderStorage].
     * The background scheduler is disabled (interval = Long.MAX_VALUE).
     * Returns parsed JSON responses in order, skipping blank lines.
     */
    private fun send(
        vararg requests: String,
        storage: ReminderStorage = ReminderStorage(tempFile)
    ): List<JsonObject> {
        val inputBytes = requests.joinToString("\n", postfix = "\n").toByteArray()
        val input = ByteArrayInputStream(inputBytes)
        val output = ByteArrayOutputStream()
        val server = McpServer(
            input = input,
            output = output,
            storage = storage,
            schedulerIntervalMs = Long.MAX_VALUE
        )
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

    /** Extracts the text content from a tool result response. */
    private fun toolText(response: JsonObject): String =
        response["result"]!!.jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

    /** Returns true if the tool result has isError=true. */
    private fun isToolError(response: JsonObject): Boolean =
        response["result"]!!.jsonObject["isError"]!!.jsonPrimitive.boolean

    // ── initialize ────────────────────────────────────────────────────────────

    @Test
    fun `initialize returns protocol version 2024-11-05`() {
        val response = send(rpc(1, "initialize")).first()
        val result = response["result"]!!.jsonObject
        assertEquals("2024-11-05", result["protocolVersion"]!!.jsonPrimitive.content)
    }

    @Test
    fun `initialize returns capabilities and serverInfo`() {
        val response = send(rpc(1, "initialize")).first()
        val result = response["result"]!!.jsonObject
        assertNotNull(result["capabilities"])
        assertNotNull(result["serverInfo"])
        assertEquals("scheduler-mcp", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    // ── ping ──────────────────────────────────────────────────────────────────

    @Test
    fun `ping returns result`() {
        val response = send(rpc(1, "ping")).first()
        assertNotNull(response["result"])
        assertNull(response["error"])
    }

    // ── tools/list ────────────────────────────────────────────────────────────

    @Test
    fun `tools list returns exactly 6 tools`() {
        val response = send(rpc(1, "tools/list")).first()
        val tools = response["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(6, tools.size)
    }

    @Test
    fun `tools list contains all expected tool names`() {
        val response = send(rpc(1, "tools/list")).first()
        val names = response["result"]!!.jsonObject["tools"]!!.jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
            .toSet()
        val expected = setOf(
            "schedule_reminder", "list_reminders", "get_due_reminders",
            "cancel_reminder", "dismiss_reminder", "get_trigger_log"
        )
        assertEquals(expected, names)
    }

    // ── unknown method ────────────────────────────────────────────────────────

    @Test
    fun `unknown method returns JSON-RPC error`() {
        val response = send(rpc(1, "unknown/method")).first()
        assertNotNull(response["error"])
        assertNull(response["result"])
        assertEquals(-32601, response["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    // ── notifications are silently ignored ───────────────────────────────────

    @Test
    fun `notification messages produce no response`() {
        val responses = send(
            rpc(1, "notifications/initialized"),
            rpc(2, "ping") // control — should still produce one response
        )
        assertEquals(1, responses.size)
        assertNotNull(responses[0]["result"]) // only ping responded
    }

    // ── schedule_reminder ─────────────────────────────────────────────────────

    @Test
    fun `schedule_reminder returns scheduled status and REM- id`() {
        val response = send(toolCall(1, "schedule_reminder",
            """{"title":"Meeting","scheduled_at":"2030-01-01T09:00:00"}"""
        )).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals("scheduled", result["status"]!!.jsonPrimitive.content)
        assertTrue(result["id"]!!.jsonPrimitive.content.matches(Regex("REM-[A-F0-9]{6}")))
    }

    @Test
    fun `schedule_reminder without title returns error`() {
        val response = send(toolCall(1, "schedule_reminder",
            """{"scheduled_at":"2030-01-01T09:00:00"}"""
        )).first()
        assertTrue(response["error"] != null || isToolError(response))
    }

    @Test
    fun `schedule_reminder without scheduled_at returns error`() {
        val response = send(toolCall(1, "schedule_reminder",
            """{"title":"No Date"}"""
        )).first()
        assertTrue(response["error"] != null || isToolError(response))
    }

    @Test
    fun `schedule_reminder with invalid date returns error`() {
        val response = send(toolCall(1, "schedule_reminder",
            """{"title":"Bad Date","scheduled_at":"not-a-date"}"""
        )).first()
        assertTrue(response["error"] != null || isToolError(response))
    }

    // ── schedule_reminder date formats ────────────────────────────────────────

    @Test
    fun `schedule_reminder accepts ISO-8601 format`() {
        val response = send(toolCall(1, "schedule_reminder",
            """{"title":"ISO","scheduled_at":"2030-03-15T09:00:00"}"""
        )).first()
        assertEquals("scheduled", Json.parseToJsonElement(toolText(response)).jsonObject["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `schedule_reminder accepts space-separated format`() {
        val response = send(toolCall(1, "schedule_reminder",
            """{"title":"Space","scheduled_at":"2030-03-15 09:00"}"""
        )).first()
        assertEquals("scheduled", Json.parseToJsonElement(toolText(response)).jsonObject["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `schedule_reminder accepts space-separated with seconds`() {
        val response = send(toolCall(1, "schedule_reminder",
            """{"title":"Space+Sec","scheduled_at":"2030-03-15 09:00:00"}"""
        )).first()
        assertEquals("scheduled", Json.parseToJsonElement(toolText(response)).jsonObject["status"]!!.jsonPrimitive.content)
    }

    // ── list_reminders ────────────────────────────────────────────────────────

    @Test
    fun `list_reminders returns empty array initially`() {
        val response = send(toolCall(1, "list_reminders")).first()
        val arr = Json.parseToJsonElement(toolText(response)).jsonArray
        assertEquals(0, arr.size)
    }

    @Test
    fun `list_reminders returns scheduled reminders`() {
        val responses = send(
            toolCall(1, "schedule_reminder", """{"title":"Task A","scheduled_at":"2030-01-01T09:00:00"}"""),
            toolCall(2, "list_reminders")
        )
        val arr = Json.parseToJsonElement(toolText(responses[1])).jsonArray
        assertEquals(1, arr.size)
        assertEquals("Task A", arr[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `list_reminders excludes dismissed by default`() {
        val storage = ReminderStorage(tempFile)
        storage.add(Reminder(id = "REM-ACTIVE", title = "Active", scheduledAt = System.currentTimeMillis() + 100_000))
        storage.add(Reminder(id = "REM-GONE", title = "Dismissed", scheduledAt = System.currentTimeMillis() + 100_000, isDismissed = true))

        val response = send(toolCall(1, "list_reminders"), storage = storage).first()
        val arr = Json.parseToJsonElement(toolText(response)).jsonArray
        assertEquals(1, arr.size)
        assertEquals("Active", arr[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `list_reminders with include_dismissed returns all`() {
        val storage = ReminderStorage(tempFile)
        storage.add(Reminder(id = "REM-ACTIVE", title = "Active", scheduledAt = System.currentTimeMillis() + 100_000))
        storage.add(Reminder(id = "REM-GONE", title = "Dismissed", scheduledAt = System.currentTimeMillis() + 100_000, isDismissed = true))

        val response = send(toolCall(1, "list_reminders", """{"include_dismissed":true}"""), storage = storage).first()
        val arr = Json.parseToJsonElement(toolText(response)).jsonArray
        assertEquals(2, arr.size)
    }

    // ── get_due_reminders ─────────────────────────────────────────────────────

    @Test
    fun `get_due_reminders returns count 0 initially`() {
        val response = send(toolCall(1, "get_due_reminders")).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals(0, result["count"]!!.jsonPrimitive.int)
    }

    @Test
    fun `get_due_reminders returns past-scheduled reminders`() {
        val storage = ReminderStorage(tempFile)
        storage.add(Reminder(id = "REM-DUE", title = "Overdue", scheduledAt = System.currentTimeMillis() - 5_000))

        val response = send(toolCall(1, "get_due_reminders"), storage = storage).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals(1, result["count"]!!.jsonPrimitive.int)
        assertEquals("Overdue", result["reminders"]!!.jsonArray[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `get_due_reminders does not return future reminders`() {
        val storage = ReminderStorage(tempFile)
        storage.add(Reminder(id = "REM-FUTURE", title = "Future", scheduledAt = System.currentTimeMillis() + 100_000))

        val response = send(toolCall(1, "get_due_reminders"), storage = storage).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals(0, result["count"]!!.jsonPrimitive.int)
    }

    // ── cancel_reminder ───────────────────────────────────────────────────────

    @Test
    fun `cancel_reminder returns cancelled status`() {
        val storage = ReminderStorage(tempFile)
        storage.add(Reminder(id = "REM-CANCEL", title = "To Cancel", scheduledAt = System.currentTimeMillis() + 100_000))

        val response = send(toolCall(1, "cancel_reminder", """{"id":"REM-CANCEL"}"""), storage = storage).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals("cancelled", result["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `cancel_reminder removes reminder from storage`() {
        val storage = ReminderStorage(tempFile)
        storage.add(Reminder(id = "REM-CANCEL", title = "To Cancel", scheduledAt = System.currentTimeMillis() + 100_000))

        send(toolCall(1, "cancel_reminder", """{"id":"REM-CANCEL"}"""), storage = storage)
        assertTrue(storage.list(true).isEmpty())
    }

    @Test
    fun `cancel_reminder returns not_found for unknown id`() {
        val response = send(toolCall(1, "cancel_reminder", """{"id":"REM-NOTEXIST"}""")).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals("not_found", result["status"]!!.jsonPrimitive.content)
    }

    // ── dismiss_reminder ──────────────────────────────────────────────────────

    @Test
    fun `dismiss_reminder marks one-time reminder as dismissed`() {
        val storage = ReminderStorage(tempFile)
        storage.add(Reminder(id = "REM-ONETIME", title = "One Time",
            scheduledAt = System.currentTimeMillis() - 1_000, recurrence = Recurrence.NONE))

        val response = send(toolCall(1, "dismiss_reminder", """{"id":"REM-ONETIME"}"""), storage = storage).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals("dismissed", result["status"]!!.jsonPrimitive.content)
        assertTrue(storage.findById("REM-ONETIME")!!.isDismissed)
    }

    @Test
    fun `dismiss_reminder advances recurring reminder and returns advanced status`() {
        val storage = ReminderStorage(tempFile)
        val now = System.currentTimeMillis()
        storage.add(Reminder(id = "REM-DAILY", title = "Daily",
            scheduledAt = now - 1_000, recurrence = Recurrence.DAILY))

        val response = send(toolCall(1, "dismiss_reminder", """{"id":"REM-DAILY"}"""), storage = storage).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals("advanced", result["status"]!!.jsonPrimitive.content)
        val advanced = storage.findById("REM-DAILY")!!
        assertTrue(advanced.scheduledAt > now, "Recurring reminder should be rescheduled to future")
    }

    @Test
    fun `dismiss_reminder returns not_found for unknown id`() {
        val response = send(toolCall(1, "dismiss_reminder", """{"id":"REM-NOTEXIST"}""")).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals("not_found", result["status"]!!.jsonPrimitive.content)
    }

    // ── get_trigger_log ───────────────────────────────────────────────────────

    @Test
    fun `get_trigger_log returns zero total_executions initially`() {
        val response = send(toolCall(1, "get_trigger_log")).first()
        val result = Json.parseToJsonElement(toolText(response)).jsonObject
        assertEquals(0, result["total_executions"]!!.jsonPrimitive.int)
        assertEquals(0, result["events"]!!.jsonArray.size)
    }

    // ── ID propagation and response correctness ────────────────────────────────

    @Test
    fun `response ID matches request ID`() {
        val responses = send(
            rpc(42, "ping"),
            rpc(99, "tools/list")
        )
        assertEquals(42, responses[0]["id"]!!.jsonPrimitive.int)
        assertEquals(99, responses[1]["id"]!!.jsonPrimitive.int)
    }

    @Test
    fun `multiple requests processed in order`() {
        val responses = send(
            rpc(1, "ping"),
            rpc(2, "initialize"),
            rpc(3, "tools/list")
        )
        assertEquals(3, responses.size)
    }

    // ── unknown tool ──────────────────────────────────────────────────────────

    @Test
    fun `calling unknown tool returns JSON-RPC error`() {
        val response = send(toolCall(1, "nonexistent_tool")).first()
        assertNotNull(response["error"])
    }
}
