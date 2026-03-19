package llmchat.rag

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for [buildRagPrompt] — verifies the citation-enforcement instructions
 * required by Trustworthy RAG are present in the generated prompt.
 */
class RagPromptTest {

    @Test
    fun `prompt contains the user question`() {
        val prompt = buildRagPrompt("some context", "Что такое HyDE?")
        assertTrue("Что такое HyDE?" in prompt)
    }

    @Test
    fun `prompt contains the context`() {
        val prompt = buildRagPrompt("relevant chunk text", "question")
        assertTrue("relevant chunk text" in prompt)
    }

    @Test
    fun `prompt instructs to use only provided context`() {
        val prompt = buildRagPrompt("ctx", "q")
        assertTrue("ТОЛЬКО" in prompt, "Must forbid use of outside knowledge")
    }

    @Test
    fun `prompt requires Источники section`() {
        val prompt = buildRagPrompt("ctx", "q")
        assertTrue("Источники" in prompt)
    }

    @Test
    fun `prompt requires Цитаты section`() {
        val prompt = buildRagPrompt("ctx", "q")
        assertTrue("Цитаты" in prompt)
    }

    @Test
    fun `prompt mentions source ID citation`() {
        val prompt = buildRagPrompt("ctx", "q")
        assertTrue("ID" in prompt, "Prompt must instruct LLM to cite chunk IDs")
    }

    @Test
    fun `empty context still produces valid prompt`() {
        val prompt = buildRagPrompt("", "вопрос")
        assertTrue("вопрос" in prompt)
        assertTrue("Источники" in prompt)
        assertTrue("Цитаты" in prompt)
    }
}
