package mcpsupport

import kotlinx.serialization.Serializable

@Serializable
data class TicketDatabase(
    val users: List<UserRecord>
)

@Serializable
data class UserRecord(
    val userId: String,
    val name: String,
    val email: String,
    val status: UserStatus,
    val subscription: SubscriptionInfo,
    val tickets: List<TicketRecord>
)

@Serializable
enum class UserStatus { active, locked, suspended }

@Serializable
data class SubscriptionInfo(
    val plan: String,
    val validUntil: String,
    val paymentStatus: String
)

@Serializable
data class TicketRecord(
    val ticketId: String,
    val subject: String,
    val status: TicketStatus,
    val createdAt: String,
    val lastUpdated: String,
    val description: String,
    val resolutionNotes: String? = null
)

@Serializable
enum class TicketStatus { open, closed, pending }
