package llmchat.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RagModeTest {

    // ── fromCliName ───────────────────────────────────────────────────────────

    @Test
    fun `fromCliName basic returns BASIC`() {
        assertEquals(RagMode.BASIC, RagMode.fromCliName("basic"))
    }

    @Test
    fun `fromCliName advanced returns ADVANCED`() {
        assertEquals(RagMode.ADVANCED, RagMode.fromCliName("advanced"))
    }

    @Test
    fun `fromCliName unknown returns null`() {
        assertNull(RagMode.fromCliName("hybrid"))
        assertNull(RagMode.fromCliName(""))
        assertNull(RagMode.fromCliName("BASIC"))   // case-sensitive
    }

    // ── availableNames ────────────────────────────────────────────────────────

    @Test
    fun `fromCliName conversational returns CONVERSATIONAL`() {
        assertEquals(RagMode.CONVERSATIONAL, RagMode.fromCliName("conversational"))
    }

    @Test
    fun `availableNames contains exactly basic, advanced and conversational`() {
        val names = RagMode.availableNames
        assertEquals(3, names.size)
        assertTrue("basic" in names)
        assertTrue("advanced" in names)
        assertTrue("conversational" in names)
    }

    // ── CliConfig.ragEnabled ──────────────────────────────────────────────────

    @Test
    fun `ragEnabled is false when ragMode is null`() {
        val config = CliConfig(ragMode = null)
        assertFalse(config.ragEnabled)
    }

    @Test
    fun `ragEnabled is true when ragMode is BASIC`() {
        val config = CliConfig(ragMode = RagMode.BASIC)
        assertTrue(config.ragEnabled)
    }

    @Test
    fun `ragEnabled is true when ragMode is ADVANCED`() {
        val config = CliConfig(ragMode = RagMode.ADVANCED)
        assertTrue(config.ragEnabled)
    }

    // ── CliConfig defaults ────────────────────────────────────────────────────

    @Test
    fun `default ragMode is null (RAG disabled)`() {
        assertNull(CliConfig().ragMode)
    }

    @Test
    fun `default similarityThreshold is 0_65`() {
        assertEquals(0.65, CliConfig().similarityThreshold)
    }

    @Test
    fun `default ragTopK is 5`() {
        assertEquals(5, CliConfig().ragTopK)
    }
}
