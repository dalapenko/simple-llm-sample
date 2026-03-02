package llmchat.cli

import llmchat.agent.memory.MemoryLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CommandParseTest {

    // ── Core commands ────────────────────────────────────────────────────────

    @Test
    fun `plain text becomes Message`() {
        assertIs<Command.Message>(Command.parse("hello world"))
        assertEquals("hello world", (Command.parse("hello world") as Command.Message).content)
    }

    @Test
    fun `empty string becomes Message`() {
        assertIs<Command.Message>(Command.parse(""))
    }

    @Test
    fun `slash-exit returns Exit`() {
        assertIs<Command.Exit>(Command.parse("/exit"))
    }

    @Test
    fun `slash-quit returns Exit`() {
        assertIs<Command.Exit>(Command.parse("/quit"))
    }

    @Test
    fun `slash-help returns Help`() {
        assertIs<Command.Help>(Command.parse("/help"))
    }

    @Test
    fun `slash-clear returns Clear`() {
        assertIs<Command.Clear>(Command.parse("/clear"))
    }

    @Test
    fun `slash-history returns History`() {
        assertIs<Command.History>(Command.parse("/history"))
    }

    @Test
    fun `slash-strategy returns StrategyInfo`() {
        assertIs<Command.StrategyInfo>(Command.parse("/strategy"))
    }

    @Test
    fun `unknown slash command returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/doesnotexist"))
    }

    // ── Branch commands ──────────────────────────────────────────────────────

    @Test
    fun `slash-branch returns BranchList`() {
        assertIs<Command.BranchList>(Command.parse("/branch"))
    }

    @Test
    fun `slash-branch-list returns BranchList`() {
        assertIs<Command.BranchList>(Command.parse("/branch list"))
    }

    @Test
    fun `slash-branch-new returns BranchNew with name`() {
        val cmd = Command.parse("/branch new feature") as Command.BranchNew
        assertEquals("feature", cmd.name)
        assertNull(cmd.fromCheckpoint)
    }

    @Test
    fun `slash-branch-new with checkpoint shorthand captures checkpoint name`() {
        val cmd = Command.parse("/branch new feature my-checkpoint") as Command.BranchNew
        assertEquals("feature", cmd.name)
        assertEquals("my-checkpoint", cmd.fromCheckpoint)
    }

    @Test
    fun `slash-branch-new without name returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/branch new"))
    }

    @Test
    fun `slash-branch-switch returns BranchSwitch with name`() {
        val cmd = Command.parse("/branch switch main") as Command.BranchSwitch
        assertEquals("main", cmd.name)
    }

    @Test
    fun `slash-branch-switch without name returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/branch switch"))
    }

    @Test
    fun `slash-branch with unknown subcommand returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/branch delete"))
    }

    // ── Checkpoint commands ──────────────────────────────────────────────────

    @Test
    fun `slash-checkpoint returns CheckpointList`() {
        assertIs<Command.CheckpointList>(Command.parse("/checkpoint"))
    }

    @Test
    fun `slash-checkpoint-list returns CheckpointList`() {
        assertIs<Command.CheckpointList>(Command.parse("/checkpoint list"))
    }

    @Test
    fun `slash-checkpoint-save returns CheckpointSave with name`() {
        val cmd = Command.parse("/checkpoint save my-save") as Command.CheckpointSave
        assertEquals("my-save", cmd.name)
    }

    @Test
    fun `slash-checkpoint-save without name returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/checkpoint save"))
    }

    @Test
    fun `slash-checkpoint with unknown subcommand returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/checkpoint delete"))
    }

    // ── Facts commands ───────────────────────────────────────────────────────

    @Test
    fun `slash-facts returns FactsList`() {
        assertIs<Command.FactsList>(Command.parse("/facts"))
    }

    @Test
    fun `slash-facts-list returns FactsList`() {
        assertIs<Command.FactsList>(Command.parse("/facts list"))
    }

    @Test
    fun `slash-facts-set returns FactsSet`() {
        val cmd = Command.parse("/facts set language Kotlin") as Command.FactsSet
        assertEquals("language", cmd.key)
        assertEquals("Kotlin", cmd.value)
    }

    @Test
    fun `slash-facts-set without key returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/facts set"))
    }

    @Test
    fun `slash-facts-set without value returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/facts set key"))
    }

    @Test
    fun `slash-facts-delete returns FactsDelete`() {
        val cmd = Command.parse("/facts delete language") as Command.FactsDelete
        assertEquals("language", cmd.key)
    }

    @Test
    fun `slash-facts-delete without key returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/facts delete"))
    }

    @Test
    fun `slash-facts with unknown subcommand returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/facts clear"))
    }

    // ── Memory commands ──────────────────────────────────────────────────────

    @Test
    fun `slash-memory-add short-term returns MemoryAdd`() {
        val cmd = Command.parse("/memory add short-term a quick note") as Command.MemoryAdd
        assertEquals(MemoryLayer.SHORT_TERM, cmd.layer)
        assertEquals("a quick note", cmd.data)
    }

    @Test
    fun `slash-memory-add work returns MemoryAdd`() {
        val cmd = Command.parse("/memory add work task context") as Command.MemoryAdd
        assertEquals(MemoryLayer.WORK, cmd.layer)
        assertEquals("task context", cmd.data)
    }

    @Test
    fun `slash-memory-add long-term returns MemoryAdd`() {
        val cmd = Command.parse("/memory add long-term important knowledge") as Command.MemoryAdd
        assertEquals(MemoryLayer.LONG_TERM, cmd.layer)
        assertEquals("important knowledge", cmd.data)
    }

    @Test
    fun `slash-memory-add without layer returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory add"))
    }

    @Test
    fun `slash-memory-add with unknown layer returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory add unknown-layer data"))
    }

    @Test
    fun `slash-memory-add without data returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory add short-term"))
    }

    @Test
    fun `slash-memory-list without layer returns MemoryList with null`() {
        val cmd = Command.parse("/memory list") as Command.MemoryList
        assertNull(cmd.layer)
    }

    @Test
    fun `slash-memory-list with layer returns MemoryList with that layer`() {
        val cmd = Command.parse("/memory list work") as Command.MemoryList
        assertEquals(MemoryLayer.WORK, cmd.layer)
    }

    @Test
    fun `slash-memory-list with unknown layer returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory list badlayer"))
    }

    @Test
    fun `slash-memory-delete returns MemoryDelete`() {
        val cmd = Command.parse("/memory delete short-term abc12345") as Command.MemoryDelete
        assertEquals(MemoryLayer.SHORT_TERM, cmd.layer)
        assertEquals("abc12345", cmd.id)
    }

    @Test
    fun `slash-memory-delete without layer returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory delete"))
    }

    @Test
    fun `slash-memory-delete with unknown layer returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory delete bad-layer id"))
    }

    @Test
    fun `slash-memory-delete without id returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory delete short-term"))
    }

    @Test
    fun `slash-memory-clear returns MemoryClear`() {
        val cmd = Command.parse("/memory clear long-term") as Command.MemoryClear
        assertEquals(MemoryLayer.LONG_TERM, cmd.layer)
    }

    @Test
    fun `slash-memory-clear without layer returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory clear"))
    }

    @Test
    fun `slash-memory-clear with unknown layer returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory clear bad"))
    }

    @Test
    fun `slash-memory with unknown subcommand returns Unknown`() {
        assertIs<Command.Unknown>(Command.parse("/memory"))
    }
}
