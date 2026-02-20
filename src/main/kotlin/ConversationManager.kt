import ai.koog.agents.core.agent.AIAgent
import kotlin.time.measureTimedValue
import kotlin.time.DurationUnit

data class RequestStatistics(
    val response: String,
    val durationMs: Long,
)

class ConversationManager(
    private val agentFactory: () -> AIAgent<String, String>
) {
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    /**
     * Send a message to the agent and get a response while maintaining conversation history
     * Returns statistics about the request including timing and token usage
     */
    suspend fun sendMessage(userMessage: String): RequestStatistics {
        // Create a new agent instance for each message to avoid single-use constraint
        val agent = agentFactory()
        
        // Measure the time taken for the request
        val timedResult = measureTimedValue {
            agent.run(userMessage)
        }
        
        val response = timedResult.value
        val durationMs = timedResult.duration.toLong(DurationUnit.MILLISECONDS)
        
        // Store in our local history for display purposes
        conversationHistory.add(userMessage to response)
        
        return RequestStatistics(
            response = response,
            durationMs = durationMs,
        )
    }

    /**
     * Clear the conversation history
     */
    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Get the conversation history
     */
    fun getHistory(): List<Pair<String, String>> {
        return conversationHistory.toList()
    }

    /**
     * Display the conversation history
     */
    fun displayHistory() {
        if (conversationHistory.isEmpty()) {
            println("No conversation history yet.")
            return
        }

        println("\n=== Conversation History ===")
        conversationHistory.forEachIndexed { index, (user, assistant) ->
            println("\n[${index + 1}] You: $user")
            println("    Assistant: $assistant")
        }
        println("===========================\n")
    }
}
