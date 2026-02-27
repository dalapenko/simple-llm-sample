package llmchat.agent.context

import ai.koog.agents.core.agent.AIAgent
import llmchat.agent.TokenCounter

private const val FACTS_EXTRACTION_SYSTEM_PROMPT = """You are a facts extractor for a conversation assistant.
Given the current known facts and a new conversation exchange, produce an updated facts list.

Rules:
- Add new important facts: user name, preferences, goals, decisions made, key technical context.
- Update existing facts if information changed.
- Remove facts that are clearly no longer relevant.
- Use short snake_case keys (e.g. user_name, programming_language, current_task).
- Keep values short and concrete.

Output format: ONE fact per line, format KEY=VALUE.
Output ONLY fact lines — no headers, no preamble, no explanations.

Example output:
user_name=Alice
preferred_language=Kotlin
current_task=building a CLI tool"""

/**
 * Sticky Facts (Key-Value Memory) context strategy.
 *
 * After each exchange the LLM is asked to update a small key-value "facts" map
 * that captures the most important persistent context (names, preferences, goals).
 * This facts block is sent together with the last [windowSize] exchanges on every
 * request, giving the model durable short-term memory without ballooning the context.
 *
 * Trade-off: one extra LLM call per exchange for fact extraction; fact quality
 * depends on the model used for extraction.
 *
 * @param windowSize   Number of recent exchanges to include alongside the facts.
 * @param agentFactory Factory used to create a throwaway agent for fact extraction.
 */
class StickyFactsStrategy(
    private val windowSize: Int = 5,
    private val agentFactory: (systemPrompt: String) -> AIAgent<String, String>
) : ContextStrategy {

    private val facts = mutableMapOf<String, String>()
    private val messages = ArrayDeque<Pair<String, String>>()

    override suspend fun addMessage(user: String, assistant: String) {
        messages.addLast(user to assistant)
        while (messages.size > windowSize) {
            messages.removeFirst()
        }
        extractAndUpdateFacts(user, assistant)
    }

    override fun loadMessages(messages: List<Pair<String, String>>) {
        this.messages.clear()
        messages.takeLast(windowSize).forEach { this.messages.addLast(it) }
        // Facts are not persisted — they will be re-extracted on the next exchange.
    }

    private suspend fun extractAndUpdateFacts(user: String, assistant: String) {
        try {
            val currentFacts = if (facts.isEmpty()) {
                "(none yet)"
            } else {
                facts.entries.joinToString("\n") { (k, v) -> "$k=$v" }
            }

            val input = buildString {
                appendLine("Current facts:")
                appendLine(currentFacts)
                appendLine()
                appendLine("New exchange:")
                appendLine("User: $user")
                appendLine("Assistant: $assistant")
                appendLine()
                append("Updated facts:")
            }

            val extractionAgent = agentFactory(FACTS_EXTRACTION_SYSTEM_PROMPT)
            val result = extractionAgent.run(input)

            val updated = result.lines()
                .map { it.trim() }
                .filter { it.contains("=") && it.isNotBlank() }
                .associate { line ->
                    val idx = line.indexOf('=')
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }

            facts.clear()
            facts.putAll(updated)
        } catch (_: Exception) {
            // Extraction failed — keep existing facts unchanged.
        }
    }

    override fun buildContextBlock(): String {
        val parts = mutableListOf<String>()

        if (facts.isNotEmpty()) {
            val factsBlock = facts.entries.joinToString("\n") { (k, v) -> "  $k: $v" }
            parts.add(
                "[KEY FACTS — important persistent context about this conversation]\n" +
                        "$factsBlock\n" +
                        "[END FACTS]"
            )
        }

        if (messages.isNotEmpty()) {
            val history = messages.joinToString("\n\n") { (user, assistant) ->
                "Previous exchange:\nUser: $user\nAssistant: $assistant"
            }
            parts.add("Recent conversation (last ${messages.size} exchanges):\n$history")
        }

        if (parts.isEmpty()) return ""
        parts.add("Please continue the conversation naturally, using the facts as background knowledge.")
        return parts.joinToString("\n\n")
    }

    override fun clearHistory() {
        messages.clear()
        facts.clear()
    }

    override fun estimateTokenStats(): ContextTokenStats {
        val msgTokens = messages.sumOf { (u, a) ->
            TokenCounter.estimate(u) + TokenCounter.estimate(a)
        }
        val factsTokens = facts.entries.sumOf { (k, v) ->
            TokenCounter.estimate("$k: $v")
        }
        return ContextTokenStats(primary = msgTokens, secondary = factsTokens)
    }

    override fun displayHistory() {
        if (facts.isEmpty() && messages.isEmpty()) {
            println("No conversation history yet.")
            return
        }
        println("\n=== Sticky Facts History ===")
        if (facts.isNotEmpty()) {
            println("\n[Facts — ${facts.size} key(s)]")
            facts.forEach { (k, v) -> println("  $k = $v") }
        } else {
            println("\n[Facts — none extracted yet]")
        }
        if (messages.isNotEmpty()) {
            println("\n[Recent — last ${messages.size}/${windowSize} exchange(s)]")
            messages.forEachIndexed { i, (user, assistant) ->
                println("\n[${i + 1}] You: $user")
                println("    Assistant: $assistant")
            }
        }
        println("===========================\n")
    }

    fun getFacts(): Map<String, String> = facts.toMap()

    fun setFact(key: String, value: String) {
        facts[key] = value
    }

    fun deleteFact(key: String) {
        facts.remove(key)
    }
}
