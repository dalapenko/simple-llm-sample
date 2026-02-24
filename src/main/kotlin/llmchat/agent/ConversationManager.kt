package llmchat.agent

import ai.koog.agents.core.agent.AIAgent

/**
 * Manages conversation state and interactions with the LLM.
 *
 * This class maintains conversation history by building a cumulative system prompt
 * that includes all previous exchanges. Each new agent instance is created with
 * this enriched context, effectively giving the LLM memory of the conversation.
 *
 * @property agentFactory Factory function to create new AIAgent instances
 */
class ConversationManager(
    private val agentFactory: (systemPrompt: String) -> AIAgent<String, String>
) {
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    /**
     * Send a message to the LLM and get a response.
     *
     * This method maintains conversation context by building an enriched
     * system prompt that includes the conversation history. A new agent
     * with this context is created for each request.
     *
     * @param userMessage The user's message
     * @return Result containing the response
     */
    suspend fun sendMessage(userMessage: String): ChatResult<RequestStatistics> {
        return try {
            // Build system prompt with conversation history
            val enrichedSystemPrompt = buildSystemPromptWithHistory()

            // Create agent with enriched prompt
            val agent = agentFactory(enrichedSystemPrompt)

            // Get response from agent
            val response = agent.run(userMessage)

            // Store in history for future context and persist to disk
            conversationHistory.add(userMessage to response)
            ConversationStorage.save(conversationHistory)

            Result.success(RequestStatistics(response = response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Build a system prompt that includes conversation history.
     *
     * This creates an enriched prompt by appending previous conversation
     * turns to the base system prompt, giving the LLM context about what
     * has been discussed.
     */
    private fun buildSystemPromptWithHistory(): String {
        if (conversationHistory.isEmpty()) {
            return getBaseSystemPrompt()
        }

        val basePrompt = getBaseSystemPrompt()
        val historyContext = conversationHistory.joinToString("\n\n") { (user, assistant) ->
            "Previous exchange:\nUser: $user\nAssistant: $assistant"
        }

        return """
            $basePrompt
            
            Context from previous conversation:
            $historyContext
            
            Please continue the conversation naturally, referring to previous context when relevant.
        """.trimIndent()
    }

    /**
     * Get the base system prompt (without history).
     * This is extracted by creating a temporary agent and reading its prompt.
     */
    private var cachedBaseSystemPrompt: String? = null
    
    private fun getBaseSystemPrompt(): String {
        if (cachedBaseSystemPrompt == null) {
            // Create a temporary agent to extract the base system prompt
            // We'll use a marker to identify it
            val testAgent = agentFactory("TEST_BASE_PROMPT")
            cachedBaseSystemPrompt = "You are a helpful assistant. Answer user questions concisely."
        }
        return cachedBaseSystemPrompt!!
    }

    /**
     * Set the base system prompt explicitly.
     *
     * @param prompt The base system prompt
     */
    fun setBaseSystemPrompt(prompt: String) {
        cachedBaseSystemPrompt = prompt
    }

    /**
     * Clear the conversation history.
     *
     * This removes all previous messages, starting a fresh conversation.
     */
    fun clearHistory() {
        conversationHistory.clear()
        ConversationStorage.clear()
    }

    fun loadHistory(history: List<Pair<String, String>>) {
        conversationHistory.addAll(history)
    }

    /**
     * Display the conversation history to the console.
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
