package llmchat.agent.profile

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileManagerTest {

    @TempDir
    lateinit var tempDir: File

    private fun manager(file: File = File(tempDir, "profile.md")) = ProfileManager(file)

    // ── Default template ─────────────────────────────────────────────────────

    @Test
    fun `default template is written when file does not exist`() {
        val file = File(tempDir, "profile.md")
        manager(file)
        assertTrue(file.exists())
        assertTrue(file.readText().isNotBlank())
    }

    @Test
    fun `default template contains expected sections`() {
        val file = File(tempDir, "profile.md")
        manager(file)
        val content = file.readText()
        assertTrue(content.contains("## Communication Style"))
        assertTrue(content.contains("## Code Preferences"))
        assertTrue(content.contains("## Output Constraints"))
    }

    @Test
    fun `writeDefaultIfMissing false does not create file`() {
        val file = File(tempDir, "custom.md")
        ProfileManager(file, writeDefaultIfMissing = false)
        // file should NOT have been created
        assertTrue(!file.exists())
    }

    @Test
    fun `writeDefaultIfMissing false with existing file loads it normally`() {
        val file = File(tempDir, "custom.md")
        file.writeText("# Custom Profile")
        val m = ProfileManager(file, writeDefaultIfMissing = false)
        assertNotNull(m.getProfile())
        assertEquals("# Custom Profile", m.getProfile()!!.content)
    }

    @Test
    fun `existing file is not overwritten with default template`() {
        val file = File(tempDir, "profile.md")
        file.writeText("# My Custom Profile")
        manager(file)
        assertEquals("# My Custom Profile", file.readText())
    }

    @Test
    fun `nested parent directories are created for default template`() {
        val file = File(tempDir, "nested/deep/profile.md")
        manager(file)
        assertTrue(file.exists())
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    @Test
    fun `profile loads content from existing file`() {
        val file = File(tempDir, "profile.md")
        file.writeText("# My Profile\n\nI like Kotlin.")
        val m = manager(file)
        assertNotNull(m.getProfile())
        assertEquals("# My Profile\n\nI like Kotlin.", m.getProfile()!!.content)
    }

    @Test
    fun `empty file yields null profile`() {
        val file = File(tempDir, "profile.md")
        file.writeText("")
        val m = ProfileManager(file)   // skip default write by using pre-existing file
        assertNull(m.getProfile())
    }

    @Test
    fun `blank whitespace-only file yields null profile`() {
        val file = File(tempDir, "profile.md")
        file.writeText("   \n  \t  ")
        val m = ProfileManager(file)
        assertNull(m.getProfile())
    }

    // ── buildPromptBlock ─────────────────────────────────────────────────────

    @Test
    fun `buildPromptBlock returns empty string when profile is null`() {
        val file = File(tempDir, "profile.md")
        file.writeText("")
        val m = ProfileManager(file)
        assertEquals("", m.buildPromptBlock())
    }

    @Test
    fun `buildPromptBlock wraps content in USER PROFILE header`() {
        val file = File(tempDir, "profile.md")
        file.writeText("# My Profile\nI like Kotlin.")
        val m = manager(file)
        val block = m.buildPromptBlock()
        assertTrue(block.startsWith("[USER PROFILE]"))
        assertTrue(block.contains("# My Profile"))
    }

    // ── reload ───────────────────────────────────────────────────────────────

    @Test
    fun `reload picks up changed file content`() {
        val file = File(tempDir, "profile.md")
        file.writeText("version 1")
        val m = manager(file)
        assertEquals("version 1", m.getProfile()!!.content)

        file.writeText("version 2")
        m.reload()
        assertEquals("version 2", m.getProfile()!!.content)
    }

    @Test
    fun `reload after file deletion sets profile to null`() {
        val file = File(tempDir, "profile.md")
        file.writeText("content")
        val m = manager(file)
        assertNotNull(m.getProfile())

        file.delete()
        m.reload()
        assertNull(m.getProfile())
    }

    // ── estimateTokens ───────────────────────────────────────────────────────

    @Test
    fun `estimateTokens returns 0 when no profile`() {
        val file = File(tempDir, "profile.md")
        file.writeText("")
        val m = ProfileManager(file)
        assertEquals(0, m.estimateTokens())
    }

    @Test
    fun `estimateTokens is positive for non-empty profile`() {
        val file = File(tempDir, "profile.md")
        file.writeText("# Profile\nI like detailed answers.")
        val m = manager(file)
        assertTrue(m.estimateTokens() > 0)
    }

    // ── filePath ─────────────────────────────────────────────────────────────

    @Test
    fun `filePath returns absolute path of profile file`() {
        val file = File(tempDir, "profile.md")
        val m = manager(file)
        assertEquals(file.absolutePath, m.filePath())
    }
}
