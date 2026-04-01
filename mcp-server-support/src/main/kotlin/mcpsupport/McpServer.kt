package mcpsupport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

@Serializable
private data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement = JsonNull,
    val method: String,
    val params: JsonElement? = null
)

class McpServer(
    private val repository: CrmRepository,
    input: InputStream = System.`in`,
    output: OutputStream = System.`out`
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val reader = input.bufferedReader()
    private val writer = PrintWriter(BufferedWriter(OutputStreamWriter(output)), true)

    private val toolDefinitions = buildJsonArray {
        add(buildJsonObject {
            put("name", "get_ticket_context")
            put(
                "description",
                "Returns full user account and ticket details for a given ticket ID. " +
                        "Use this to understand the user's account status, subscription, and issue history before responding."
            )
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add("ticket_id") })
                put("properties", buildJsonObject {
                    put("ticket_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Ticket ID, e.g. TKT-201")
                    })
                })
            })
        })
        add(buildJsonObject {
            put("name", "list_user_tickets")
            put(
                "description",
                "Returns a summary list of all tickets for a given user. Accepts user ID or email address."
            )
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("required", buildJsonArray { add("user_identifier") })
                put("properties", buildJsonObject {
                    put("user_identifier", buildJsonObject {
                        put("type", "string")
                        put("description", "User ID (e.g. USR-001) or email address")
                    })
                })
            })
        })
    }

    suspend fun run() {
        System.err.println("[MCP-Support] Support CRM MCP server started")
        repository.load()

        while (true) {
            val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
            if (line.isBlank()) continue
            System.err.println("[MCP-Support] ← ${line.take(200)}")

            val response = try {
                val request = json.decodeFromString<JsonRpcRequest>(line)
                dispatch(request)
            } catch (e: SerializationException) {
                buildError(JsonNull, -32700, "Parse error: ${e.message}")
            } catch (e: Exception) {
                System.err.println("[MCP-Support] Unexpected error: ${e.message}")
                buildError(JsonNull, -32603, "Internal error: ${e.message}")
            }

            if (response != null) {
                val out = json.encodeToString(response)
                System.err.println("[MCP-Support] → [response sent, ${out.length} chars]")
                writer.println(out)
            }
        }
        System.err.println("[MCP-Support] Server shutting down")
    }

    private suspend fun dispatch(request: JsonRpcRequest): JsonElement? = when (request.method) {
        "initialize" -> handleInitialize(request.id)
        "tools/list" -> handleToolsList(request.id)
        "tools/call" -> handleToolCall(request.id, request.params?.jsonObject ?: JsonObject(emptyMap()))
        "ping" -> buildOk(request.id, JsonObject(emptyMap()))
        "notifications/initialized", "notifications/cancelled", "notifications/progress" -> null
        else -> buildError(request.id, -32601, "Method not found: ${request.method}")
    }

    private fun handleInitialize(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject { put("tools", JsonObject(emptyMap())) })
            put("serverInfo", buildJsonObject {
                put("name", "support-crm-mcp")
                put("version", "1.0.0")
            })
        })

    private fun handleToolsList(id: JsonElement): JsonElement =
        buildOk(id, buildJsonObject { put("tools", toolDefinitions) })

    private suspend fun handleToolCall(id: JsonElement, params: JsonObject): JsonElement {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return buildError(id, -32602, "Missing required parameter: name")
        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        return try {
            val text = when (name) {
                "get_ticket_context" -> executeGetTicketContext(arguments)
                "list_user_tickets" -> executeListUserTickets(arguments)
                else -> return buildError(id, -32602, "Unknown tool: $name")
            }
            buildToolResult(id, text, isError = false)
        } catch (e: IllegalArgumentException) {
            buildError(id, -32602, e.message ?: "Invalid parameters")
        } catch (e: Exception) {
            System.err.println("[MCP-Support] Tool error: ${e.message}")
            buildToolResult(id, "Tool execution failed: ${e.message}", isError = true)
        }
    }

    private fun executeGetTicketContext(args: JsonObject): String {
        val ticketId = args["ticket_id"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw IllegalArgumentException("ticket_id is required")

        val (user, ticket) = repository.findTicketById(ticketId)
            ?: return "Ticket $ticketId not found in the CRM database."

        return buildString {
            appendLine("=== TICKET CONTEXT ===")
            appendLine("Ticket ID: ${ticket.ticketId}")
            appendLine("Subject: ${ticket.subject}")
            appendLine("Status: ${ticket.status}")
            appendLine("Created: ${ticket.createdAt}")
            appendLine("Last Updated: ${ticket.lastUpdated}")
            appendLine("Description: ${ticket.description}")
            if (!ticket.resolutionNotes.isNullOrBlank()) {
                appendLine("Resolution Notes: ${ticket.resolutionNotes}")
            }
            appendLine()
            appendLine("=== USER ACCOUNT ===")
            appendLine("Name: ${user.name}")
            appendLine("Account Status: ${user.status}")
            appendLine("Subscription Plan: ${user.subscription.plan}")
            appendLine("Subscription Valid Until: ${user.subscription.validUntil}")
            appendLine("Payment Status: ${user.subscription.paymentStatus}")
            appendLine("Total Tickets: ${user.tickets.size}")
        }
    }

    private fun executeListUserTickets(args: JsonObject): String {
        val userIdentifier = args["user_identifier"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw IllegalArgumentException("user_identifier is required")

        val (user, tickets) = repository.findUserByIdentifier(userIdentifier)
            ?: return "User '$userIdentifier' not found in the CRM database."

        if (tickets.isEmpty()) return "No tickets found for user: ${user.name}"

        return buildString {
            // Only log non-PII metadata to avoid exposing email in logs
            appendLine("User: ${user.name} | Status: ${user.status} | Plan: ${user.subscription.plan}")
            appendLine()
            tickets.forEach { t ->
                appendLine("[${t.ticketId}] ${t.subject} — ${t.status} (updated: ${t.lastUpdated})")
            }
        }
    }

    // ── Response builders (identical pattern to mcp-server-git) ──────────────

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
