package mcpsupport

import kotlinx.serialization.json.Json
import java.io.File

class CrmRepository(private val dataFilePath: String) {

    private val json = Json { ignoreUnknownKeys = true }
    private var db: TicketDatabase? = null

    fun load() {
        val file = File(dataFilePath)
        if (!file.exists()) {
            System.err.println("[MCP-Support] Data file not found: $dataFilePath")
            db = TicketDatabase(emptyList())
            return
        }
        db = try {
            json.decodeFromString<TicketDatabase>(file.readText())
        } catch (e: Exception) {
            System.err.println("[MCP-Support] Failed to parse data file: ${e.message}")
            TicketDatabase(emptyList())
        }
        // Basic validation: reject records with blank userId
        db?.users?.forEach { user ->
            require(user.userId.isNotBlank()) { "userId must not be blank" }
            require(user.email.isNotBlank()) { "email must not be blank" }
        }
        System.err.println("[MCP-Support] Loaded ${db?.users?.size ?: 0} users from $dataFilePath")
    }

    fun findTicketById(ticketId: String): Pair<UserRecord, TicketRecord>? {
        val safeId = ticketId.trim()
        return db?.users?.flatMap { user ->
            user.tickets.map { ticket -> Pair(user, ticket) }
        }?.find { (_, ticket) -> ticket.ticketId.equals(safeId, ignoreCase = true) }
    }

    fun findUserByIdentifier(userIdentifier: String): Pair<UserRecord, List<TicketRecord>>? {
        val safeId = userIdentifier.trim()
        return db?.users?.find { user ->
            user.userId.equals(safeId, ignoreCase = true) ||
                    user.email.equals(safeId, ignoreCase = true)
        }?.let { user -> Pair(user, user.tickets) }
    }
}
