package llmchat.cli

import llmchat.model.SupportedModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CliParserTest {

    @Test
    fun `empty args returns default config`() {
        val config = CliParser.parse(emptyArray())
        assertEquals(SupportedModel.default, config.model)
        assertEquals(StrategyType.default, config.strategyType)
        assertEquals(1.0, config.temperature)
        assertEquals(10, config.contextWindow.windowSize)
        assertEquals(10, config.contextWindow.summaryBatchSize)
    }

    // ── --system-prompt ──────────────────────────────────────────────────────

    @Test
    fun `system-prompt is set correctly`() {
        val config = CliParser.parse(arrayOf("--system-prompt", "You are a pirate."))
        assertEquals("You are a pirate.", config.systemPrompt)
    }

    @Test
    fun `system-prompt without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--system-prompt"))
        }
    }

    // ── --temperature ────────────────────────────────────────────────────────

    @Test
    fun `temperature 0_0 is accepted`() {
        val config = CliParser.parse(arrayOf("--temperature", "0.0"))
        assertEquals(0.0, config.temperature)
    }

    @Test
    fun `temperature 1_5 is accepted`() {
        val config = CliParser.parse(arrayOf("--temperature", "1.5"))
        assertEquals(1.5, config.temperature)
    }

    @Test
    fun `temperature 2_0 is accepted (upper bound)`() {
        val config = CliParser.parse(arrayOf("--temperature", "2.0"))
        assertEquals(2.0, config.temperature)
    }

    @Test
    fun `temperature above 2_0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--temperature", "2.1"))
        }
    }

    @Test
    fun `temperature below 0_0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--temperature", "-0.1"))
        }
    }

    @Test
    fun `temperature non-numeric throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--temperature", "hot"))
        }
    }

    @Test
    fun `temperature without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--temperature"))
        }
    }

    // ── --model ──────────────────────────────────────────────────────────────

    @Test
    fun `model gpt-4o is parsed correctly`() {
        val config = CliParser.parse(arrayOf("--model", "gpt-4o"))
        assertEquals(SupportedModel.GPT4O, config.model)
    }

    @Test
    fun `model mistral-7b is parsed correctly`() {
        val config = CliParser.parse(arrayOf("--model", "mistral-7b"))
        assertEquals(SupportedModel.MISTRAL_7B, config.model)
    }

    @Test
    fun `unknown model name throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--model", "unknown-model"))
        }
    }

    @Test
    fun `model without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--model"))
        }
    }

    // ── --context-window ─────────────────────────────────────────────────────

    @Test
    fun `context-window is set correctly`() {
        val config = CliParser.parse(arrayOf("--context-window", "5"))
        assertEquals(5, config.contextWindow.windowSize)
    }

    @Test
    fun `context-window of 1 is valid`() {
        val config = CliParser.parse(arrayOf("--context-window", "1"))
        assertEquals(1, config.contextWindow.windowSize)
    }

    @Test
    fun `context-window of 0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--context-window", "0"))
        }
    }

    @Test
    fun `context-window non-integer throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--context-window", "abc"))
        }
    }

    @Test
    fun `context-window without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--context-window"))
        }
    }

    // ── --summary-batch ──────────────────────────────────────────────────────

    @Test
    fun `summary-batch is set correctly`() {
        val config = CliParser.parse(arrayOf("--summary-batch", "3"))
        assertEquals(3, config.contextWindow.summaryBatchSize)
    }

    @Test
    fun `summary-batch of 0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--summary-batch", "0"))
        }
    }

    @Test
    fun `summary-batch without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--summary-batch"))
        }
    }

    // ── --strategy ───────────────────────────────────────────────────────────

    @Test
    fun `strategy layered is parsed correctly`() {
        val config = CliParser.parse(arrayOf("--strategy", "layered"))
        assertEquals(StrategyType.LAYERED, config.strategyType)
    }

    @Test
    fun `strategy branching is parsed correctly`() {
        val config = CliParser.parse(arrayOf("--strategy", "branching"))
        assertEquals(StrategyType.BRANCHING, config.strategyType)
    }

    @Test
    fun `unknown strategy throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--strategy", "neural"))
        }
    }

    @Test
    fun `strategy without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--strategy"))
        }
    }

    // ── --help / -h ──────────────────────────────────────────────────────────

    @Test
    fun `--help sets showHelp true`() {
        assertTrue(CliParser.parse(arrayOf("--help")).showHelp)
    }

    @Test
    fun `-h sets showHelp true`() {
        assertTrue(CliParser.parse(arrayOf("-h")).showHelp)
    }

    // ── unknown flags ────────────────────────────────────────────────────────

    @Test
    fun `unknown flag throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--unknown"))
        }
    }

    // ── combined args ────────────────────────────────────────────────────────

    @Test
    fun `multiple valid flags are all applied`() {
        val config = CliParser.parse(
            arrayOf("--model", "gpt-4o", "--temperature", "0.7", "--strategy", "branching")
        )
        assertEquals(SupportedModel.GPT4O, config.model)
        assertEquals(0.7, config.temperature)
        assertEquals(StrategyType.BRANCHING, config.strategyType)
    }
}
