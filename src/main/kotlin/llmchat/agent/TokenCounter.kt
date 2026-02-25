package llmchat.agent

/**
 * Estimates token count for LLM inputs and outputs.
 *
 * Uses the widely accepted ~4 characters per token approximation,
 * which is accurate to within ~15% for English text with GPT-family models.
 */
object TokenCounter {
    private const val CHARS_PER_TOKEN = 4

    fun estimate(text: String): Int {
        if (text.isBlank()) return 0
        return maxOf(1, text.length / CHARS_PER_TOKEN)
    }
}
