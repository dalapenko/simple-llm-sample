package llmchat.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CliParserRagFlagsTest {

    // ── --rag (backward-compat shorthand) ─────────────────────────────────────

    @Test
    fun `--rag enables BASIC mode`() {
        val config = CliParser.parse(arrayOf("--rag"))
        assertEquals(RagMode.BASIC, config.ragMode)
    }

    @Test
    fun `--no-rag disables RAG`() {
        val config = CliParser.parse(arrayOf("--no-rag"))
        assertNull(config.ragMode)
    }

    @Test
    fun `--rag followed by --no-rag disables RAG`() {
        val config = CliParser.parse(arrayOf("--rag", "--no-rag"))
        assertNull(config.ragMode)
    }

    // ── --mode ────────────────────────────────────────────────────────────────

    @Test
    fun `--mode basic sets BASIC`() {
        val config = CliParser.parse(arrayOf("--mode", "basic"))
        assertEquals(RagMode.BASIC, config.ragMode)
    }

    @Test
    fun `--mode advanced sets ADVANCED`() {
        val config = CliParser.parse(arrayOf("--mode", "advanced"))
        assertEquals(RagMode.ADVANCED, config.ragMode)
    }

    @Test
    fun `--mode without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--mode"))
        }
    }

    @Test
    fun `--mode unknown value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--mode", "hybrid"))
        }
    }

    @Test
    fun `--mode overrides --rag shorthand`() {
        val config = CliParser.parse(arrayOf("--rag", "--mode", "advanced"))
        assertEquals(RagMode.ADVANCED, config.ragMode)
    }

    // ── --threshold ───────────────────────────────────────────────────────────

    @Test
    fun `--threshold 0_7 is accepted`() {
        val config = CliParser.parse(arrayOf("--threshold", "0.7"))
        assertEquals(0.7, config.similarityThreshold)
    }

    @Test
    fun `--threshold 0_0 (lower bound) is accepted`() {
        val config = CliParser.parse(arrayOf("--threshold", "0.0"))
        assertEquals(0.0, config.similarityThreshold)
    }

    @Test
    fun `--threshold 1_0 (upper bound) is accepted`() {
        val config = CliParser.parse(arrayOf("--threshold", "1.0"))
        assertEquals(1.0, config.similarityThreshold)
    }

    @Test
    fun `--threshold above 1_0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--threshold", "1.1"))
        }
    }

    @Test
    fun `--threshold below 0_0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--threshold", "-0.1"))
        }
    }

    @Test
    fun `--threshold non-numeric throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--threshold", "high"))
        }
    }

    @Test
    fun `--threshold without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--threshold"))
        }
    }

    // ── --top-k ───────────────────────────────────────────────────────────────

    @Test
    fun `--top-k 3 is accepted`() {
        val config = CliParser.parse(arrayOf("--top-k", "3"))
        assertEquals(3, config.ragTopK)
    }

    @Test
    fun `--top-k 1 (minimum) is accepted`() {
        val config = CliParser.parse(arrayOf("--top-k", "1"))
        assertEquals(1, config.ragTopK)
    }

    @Test
    fun `--top-k 0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--top-k", "0"))
        }
    }

    @Test
    fun `--top-k non-integer throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--top-k", "three"))
        }
    }

    @Test
    fun `--top-k without value throws`() {
        assertFailsWith<IllegalArgumentException> {
            CliParser.parse(arrayOf("--top-k"))
        }
    }

    // ── combined ──────────────────────────────────────────────────────────────

    @Test
    fun `mode advanced with threshold and top-k all applied`() {
        val config = CliParser.parse(
            arrayOf("--mode", "advanced", "--threshold", "0.75", "--top-k", "3")
        )
        assertEquals(RagMode.ADVANCED, config.ragMode)
        assertEquals(0.75, config.similarityThreshold)
        assertEquals(3, config.ragTopK)
    }

    @Test
    fun `RAG flags can be combined with model and temperature`() {
        val config = CliParser.parse(
            arrayOf("--mode", "advanced", "--temperature", "0.5", "--top-k", "5")
        )
        assertEquals(RagMode.ADVANCED, config.ragMode)
        assertEquals(0.5, config.temperature)
        assertEquals(5, config.ragTopK)
    }
}
