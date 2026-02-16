import ai.koog.agents.core.agent.AIAgent

class ConversationManager(private val agent: AIAgent<String, String>) {
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    /**
     * Send a message to the agent and get a response while maintaining conversation history
     */
    suspend fun sendMessage(userMessage: String): String {
        val response = agent.run(userMessage)
        
        // Store in our local history for display purposes
        conversationHistory.add(userMessage to response)
        
        return response
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
