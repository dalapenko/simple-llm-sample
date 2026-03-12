package mcpscheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// ── JSON-RPC 2.0 request model ────────────────────────────────────────────────

@Serializable
private data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement = JsonNull,
    val method: String,
    val params: JsonElement? = null
)

// ── Server ────────────────────────────────────────────────────────────────────

class McpServer(
    input: InputStream = System.`in`,
    output: OutputStream = System.`out`,
    private val storage: ReminderStorage = ReminderStorage(),
    private val schedulerIntervalMs: Long = System.getenv("SCHEDULER_INTERVAL_MS")?.toLongOrNull() ?: 60_000L
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val reader = input.bufferedReader()
    private val writer = PrintWriter(BufferedWriter(OutputStreamWriter(output)), true)

    /** In-memory log of every auto-execution performed by the background coroutine. */
    private val triggerLog = mutableListOf<TriggerEvent>()

    /**
     * IDs of reminders for which a [NOTIFY] has already been emitted this session.
     * Prevents duplicate push notifications for one-time reminders that stay due.
     */
    private val notifiedDueIds = mutableSetOf<String>()

    /**
     * Emit a structured push notification on stderr so the CLI can intercept it.
     * Format: [NOTIFY] <title>\t<description>
     * The CLI reads the subprocess's stderr and reacts to these lines.
     */
    private fun emitNotification(title: String, description: String) {
        System.err.println("[NOTIFY] $title\t$description")
    }

    // ── Tool schema definitions ───────────────────────────────────────────────

    private val toolDefinitions = buildJsonArray {
        add(buildJsonObject {
            put("name", "schedule_reminder")
            put("description", "Schedules a new reminder at the given date/time. Returns the created reminder details.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("title", buildJsonObject {
                        put("type", "string")
                        put("description", "Title of the reminder")
                    })
                    put("scheduled_at", buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            "Date and time when the reminder should trigger. Accepted formats: ISO-8601 (2026-03-15T09:00:00), space-separated (2026-03-15 09:00), or Russian (15 марта 2026 09:00)."
                        )
                    })
                    put("description", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional description for the reminder")
                    })
                    put("recurrence", buildJsonObject {
                        put("type", "string")
                        put("description", "Recurrence pattern: none, daily, weekly, or monthly")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("none"))
                            add(JsonPrimitive("daily"))
                            add(JsonPrimitive("weekly"))
                            add(JsonPrimitive("monthly"))
                        })
                    })
                    put("recurrence_interval_days", buildJsonObject {
                        put("type", "integer")
                        put("description", "Interval in days for recurrence (default 1)")
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("title"))
                    add(JsonPrimitive("scheduled_at"))
                })
            })
        })
        add(buildJsonObject {
            put("name", "list_reminders")
            put("description", "Lists all active reminders. Optionally includes dismissed ones.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("include_dismissed", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to include dismissed reminders (default false)")
                    })
                })
            })
        })
        add(buildJsonObject {
            put("name", "get_due_reminders")
            put(
                "description",
                "Returns all reminders that are currently due (scheduled time has passed and not dismissed)."
            )
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(emptyMap()))
            })
        })
        add(buildJsonObject {
            put("name", "cancel_reminder")
            put("description", "Permanently removes a reminder by its ID.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "The reminder ID (e.g. REM-A1B2C3)")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("id")) })
            })
        })
        add(buildJsonObject {
            put("name", "dismiss_reminder")
            put("description", "Dismisses a due reminder. For recurring reminders, advances to the next occurrence.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "string")
                        put("description", "The reminder ID (e.g. REM-A1B2C3)")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("id")) })
            })
        })
        add(buildJsonObject {
            put("name", "get_trigger_log")
            put(
                "description",
                "Returns the log of all reminders that were automatically executed by the background scheduler. Each entry shows which reminder fired, when it fired, and what the next scheduled time is."
            )
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(emptyMap()))
            })
        })
    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    suspend fun run() {
        System.err.println("[MCP] Scheduler MCP server started (interval=${schedulerIntervalMs}ms)")

        val backgroundJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(schedulerIntervalMs)
                runScheduledActions()
            }
        }

        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (line.isBlank()) continue

                System.err.println("[MCP] ← $line")

                val response = try {
                    val request = json.decodeFromString<JsonRpcRequest>(line)
                    dispatch(request)
                } catch (e: SerializationException) {
                    buildError(JsonNull, -32700, "Parse error: ${e.message}")
                } catch (e: Exception) {
                    System.err.println("[MCP] Unexpected error: ${e.message}")
                    buildError(JsonNull, -32603, "Internal error: ${e.message}")
                }

                if (response != null) {
                    val out = json.encodeToString(response)
                    System.err.println("[MCP] → $out")
                    writer.println(out)
                }
            }
        } finally {
            backgroundJob.cancel()
            System.err.println("[MCP] Server shutting down")
        }
    }

    // ── Background scheduler ──────────────────────────────────────────────────

    /**
     * Periodic action executed by the background coroutine on every scheduler tick:
     * 1. Auto-advances due recurring reminders to their next occurrence → triggerLog + [NOTIFY]
     * 2. Emits [NOTIFY] for newly-due one-time reminders (first time they become due)
     *
     * [NOTIFY] lines on stderr are the server's push channel to the CLI.
     * The CLI reads this subprocess's stderr and reacts without knowing anything
     * about the server's internal storage format.
     */
    private fun runScheduledActions() {
        System.err.println("[MCP] Scheduler tick")

        // 1. Auto-advance recurring reminders and notify each execution
        val fired = storage.autoAdvanceRecurring()
        fired.forEach { ev ->
            triggerLog.add(ev)
            System.err.println("[MCP] Auto-advanced ${ev.reminderId} '${ev.title}' → next: ${ev.nextScheduledAt}")
            emitNotification(ev.title, ev.title)
        }

        // 2. Emit one-shot notifications for newly-due one-time reminders
        storage.getDue()
            .filter { it.id !in notifiedDueIds }
            .forEach { reminder ->
                notifiedDueIds.add(reminder.id)
                System.err.println("[MCP] Due one-time reminder: ${reminder.id} '${reminder.title}'")
                emitNotification(reminder.title, reminder.description)
            }
    }

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private fun dispatch(request: JsonRpcRequest): JsonElement? = when (request.method) {
        "initialize" -> handleInitialize(request.id)
        "tools/list" -> handleToolsList(request.id)
        "tools/call" -> handleToolCall(request.id, request.params?.jsonObject ?: JsonObject(emptyMap()))
        "ping" -> buildOk(request.id, JsonObject(emptyMap()))
        "notifications/initialized",
        "notifications/cancelled",
        "notifications/progress" -> null

        else -> buildError(request.id, -32601, "Method not found: ${request.method}")
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handleInitialize(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {
                put("tools", JsonObject(emptyMap()))
            })
            put("serverInfo", buildJsonObject {
                put("name", "scheduler-mcp")
                put("version", "1.0.0")
            })
        })

    private fun handleToolsList(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject {
            put("tools", toolDefinitions)
        })

    private fun handleToolCall(id: JsonElement, params: JsonObject): JsonElement {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return buildError(id, -32602, "Missing required parameter: name")
        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        return try {
            val text = when (name) {
                "schedule_reminder" -> executeScheduleReminder(arguments)
                "list_reminders" -> executeListReminders(arguments)
                "get_due_reminders" -> executeGetDueReminders()
                "cancel_reminder" -> executeCancelReminder(arguments)
                "dismiss_reminder" -> executeDismissReminder(arguments)
                "get_trigger_log" -> executeGetTriggerLog()
                else -> return buildError(id, -32602, "Unknown tool: $name")
            }
            buildToolResult(id, text, isError = false)
        } catch (e: IllegalArgumentException) {
            buildError(id, -32602, e.message ?: "Invalid parameters")
        } catch (e: Exception) {
            System.err.println("[MCP] Tool error: ${e.message}")
            buildToolResult(id, "Tool execution failed: ${e.message}", isError = true)
        }
    }

    // ── Tool implementations ──────────────────────────────────────────────────

    private fun executeScheduleReminder(args: JsonObject): String {
        val title = args["title"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("title is required")
        val scheduledAtStr = args["scheduled_at"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("scheduled_at is required")
        val description = args["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val recurrenceStr = args["recurrence"]?.jsonPrimitive?.contentOrNull ?: "none"
        val recurrenceIntervalDays = args["recurrence_interval_days"]?.jsonPrimitive?.intOrNull ?: 1

        val scheduledAtMillis = try {
            parseDateTime(scheduledAtStr).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: IllegalArgumentException) {
            throw e
        }

        val recurrence = try {
            Recurrence.valueOf(recurrenceStr.uppercase())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid recurrence: $recurrenceStr. Must be one of: none, daily, weekly, monthly")
        }

        val reminder = Reminder(
            id = Reminder.generateId(),
            title = title,
            description = description,
            scheduledAt = scheduledAtMillis,
            recurrence = recurrence,
            recurrenceIntervalDays = recurrenceIntervalDays
        )
        storage.add(reminder)

        return json.encodeToString(buildJsonObject {
            put("status", "scheduled")
            put("id", reminder.id)
            put("title", reminder.title)
            put("scheduled_at", scheduledAtStr)
            put("recurrence", recurrenceStr.lowercase())
        })
    }

    private fun executeListReminders(args: JsonObject): String {
        val includeDismissed = args["include_dismissed"]?.jsonPrimitive?.booleanOrNull ?: false
        val reminders = storage.list(includeDismissed)

        return json.encodeToString(buildJsonArray {
            reminders.forEach { r ->
                add(buildJsonObject {
                    put("id", r.id)
                    put("title", r.title)
                    put("description", r.description)
                    put("scheduled_at", formatEpochMs(r.scheduledAt))
                    put("recurrence", r.recurrence.name.lowercase())
                    put("is_dismissed", r.isDismissed)
                    put("created_at", formatEpochMs(r.createdAt))
                })
            }
        })
    }

    private fun executeGetDueReminders(): String {
        val due = storage.getDue()
        return json.encodeToString(buildJsonObject {
            put("count", due.size)
            put("reminders", buildJsonArray {
                due.forEach { r ->
                    add(buildJsonObject {
                        put("id", r.id)
                        put("title", r.title)
                        put("description", r.description)
                        put("scheduled_at", formatEpochMs(r.scheduledAt))
                        put("recurrence", r.recurrence.name.lowercase())
                    })
                }
            })
        })
    }

    private fun executeCancelReminder(args: JsonObject): String {
        val id = args["id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("id is required")
        val removed = storage.cancel(id)
        return json.encodeToString(buildJsonObject {
            put("status", if (removed) "cancelled" else "not_found")
            put("id", id)
        })
    }

    private fun executeDismissReminder(args: JsonObject): String {
        val id = args["id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("id is required")
        val reminder = storage.findById(id)
            ?: return json.encodeToString(buildJsonObject {
                put("status", "not_found")
                put("id", id)
            })

        if (reminder.recurrence == Recurrence.NONE) {
            storage.update(reminder.copy(isDismissed = true))
            return json.encodeToString(buildJsonObject {
                put("status", "dismissed")
                put("id", reminder.id)
            })
        }

        // Recurring: advance to next occurrence
        val currentLdt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(reminder.scheduledAt),
            ZoneId.systemDefault()
        )
        val nextLdt = when (reminder.recurrence) {
            Recurrence.DAILY -> currentLdt.plusDays(reminder.recurrenceIntervalDays.toLong())
            Recurrence.WEEKLY -> currentLdt.plusWeeks(reminder.recurrenceIntervalDays.toLong())
            Recurrence.MONTHLY -> currentLdt.plusMonths(reminder.recurrenceIntervalDays.toLong())
            Recurrence.NONE -> currentLdt // unreachable
        }
        val nextMillis = nextLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        storage.update(
            reminder.copy(
                scheduledAt = nextMillis,
                lastTriggeredAt = System.currentTimeMillis()
            )
        )

        return json.encodeToString(buildJsonObject {
            put("status", "advanced")
            put("id", reminder.id)
            put("next_scheduled_at", nextMillis)
        })
    }

    private fun executeGetTriggerLog(): String =
        json.encodeToString(buildJsonObject {
            put("total_executions", triggerLog.size)
            put("events", buildJsonArray {
                triggerLog.forEach { ev ->
                    add(buildJsonObject {
                        put("reminder_id", ev.reminderId)
                        put("title", ev.title)
                        put("triggered_at", formatEpochMs(ev.triggeredAt))
                        put("next_scheduled_at", formatEpochMs(ev.nextScheduledAt))
                    })
                }
            })
        })

    // ── Date formatting ───────────────────────────────────────────────────────

    private val displayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", Locale.of("ru"))

    private fun formatEpochMs(epochMs: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
            .format(displayFormatter)

    // ── Date parsing ──────────────────────────────────────────────────────────

    private val dateFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,                               // 2026-03-15T09:00:00
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),                  // 2026-03-15 09:00:00
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),                     // 2026-03-15 09:00
        DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm:ss", Locale.of("ru")),    // 15 марта 2026 09:00:05
        DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", Locale.of("ru")),      // 15 марта 2026 09:00
        DateTimeFormatter.ofPattern("d MMMM yyyy H:mm", Locale.of("ru")),       // 15 марта 2026 9:00
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.of("ru")),       // 15 мар 2026 09:00
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("ru")),            // 15 марта 2026  (midnight)
    )

    private fun parseDateTime(input: String): LocalDateTime {
        val s = input.trim()
        for (fmt in dateFormatters) {
            try {
                return LocalDateTime.parse(s, fmt)
            } catch (_: Exception) {
            }
        }
        throw IllegalArgumentException(
            "Cannot parse date-time: \"$s\". " +
                    "Accepted: ISO-8601 (2026-03-15T09:00), space (2026-03-15 09:00), or Russian (15 марта 2026 09:00)."
        )
    }

    // ── Response builders ─────────────────────────────────────────────────────

    private fun buildOk(id: JsonElement, result: JsonElement): JsonElement =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }

    private fun buildError(id: JsonElement, code: Int, message: String): JsonElement =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }

    private fun buildToolResult(id: JsonElement, text: String, isError: Boolean): JsonElement =
        buildOk(id, buildJsonObject {
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
            put("isError", isError)
        })
}
