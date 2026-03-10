package llmchat.agent.profile

import llmchat.agent.TokenCounter
import java.io.File

/**
 * Manages the user profile markdown file at [profileFile] (default: ~/.llmchat/profile.md).
 *
 * On first launch the default template is written automatically so the user
 * has a documented starting point. I/O errors are caught and logged to stderr,
 * never propagated — the profile degrades gracefully to absent rather than
 * crashing the app. This matches the convention established in LongTermStore.
 *
 * @param profileFile        Path to the profile markdown file.
 * @param writeDefaultIfMissing  When true (default), writes the default template if [profileFile]
 *                           does not exist. Pass false for user-specified paths so the app does
 *                           not silently create a file the user did not ask for.
 */
class ProfileManager(
    private val profileFile: File = defaultProfileFile(),
    private val writeDefaultIfMissing: Boolean = true
) {
    private var profile: UserProfile? = null

    init {
        if (writeDefaultIfMissing) ensureDefaultExists()
        reload()
    }

    /** The currently loaded profile, or null if absent / empty / unreadable. */
    fun getProfile(): UserProfile? = profile

    /** Re-read [profileFile] from disk. Call after the user edits the file. */
    fun reload() {
        profile = loadFromDisk()
    }

    /** Absolute path shown to the user in /profile path and /profile show. */
    fun filePath(): String = profileFile.absolutePath

    /**
     * Builds the system-prompt block injected on every request.
     * Returns an empty string when no profile is active (file missing / blank).
     */
    fun buildPromptBlock(): String {
        val p = profile ?: return ""
        return "[USER PROFILE]\n${p.content.trim()}"
    }

    /** Estimates the token cost of the profile block (0 when profile is absent). */
    fun estimateTokens(): Int {
        profile ?: return 0
        return TokenCounter.estimate(buildPromptBlock())
    }

    private fun loadFromDisk(): UserProfile? {
        if (!profileFile.exists()) return null
        return try {
            val text = profileFile.readText()
            if (text.isBlank()) {
                System.err.println("[ProfileManager] Profile file is empty, skipping.")
                null
            } else {
                UserProfile(text)
            }
        } catch (e: Exception) {
            System.err.println("[ProfileManager] Failed to read profile: ${e.message}")
            null
        }
    }

    private fun ensureDefaultExists() {
        if (profileFile.exists()) return
        try {
            profileFile.parentFile?.mkdirs()
            profileFile.writeText(DEFAULT_PROFILE_TEMPLATE)
        } catch (e: Exception) {
            System.err.println("[ProfileManager] Could not write default profile: ${e.message}")
        }
    }

    companion object {
        fun defaultProfileFile(): File =
            File(System.getProperty("user.home"), ".llmchat/profile.md")

        val DEFAULT_PROFILE_TEMPLATE: String = """
# User Profile

<!--
  This file personalizes every conversation in llmchat.
  Edit any section — the full content is injected into the system prompt on every request.
  Reload with:  /profile reload
  View path:    /profile path
-->

## Communication Style

- Preferred language: English
- Response length: concise — short, focused answers; expand only when complexity demands it
- Tone: direct and professional; skip pleasantries
- Format: use Markdown when it adds clarity (code blocks, lists); plain prose otherwise

## Code Preferences

- Primary language: Kotlin
- Style: idiomatic Kotlin — prefer data classes, sealed classes, extension functions, scope functions
- Naming: follow Kotlin coding conventions (camelCase, descriptive names)
- Comments: only when intent is not obvious from the code; no redundant comments
- Error handling: explicit — prefer Result/sealed class over exceptions for domain errors
- Null safety: leverage Kotlin's null safety; avoid `!!` except in guaranteed-safe contexts

## Output Constraints

- Do not repeat the question back before answering
- Do not add disclaimers or caveats unless they are technically material
- Do not use filler phrases ("Certainly!", "Great question!", "Sure!")
- Prefer concrete examples over abstract explanations

## Context

<!-- Add personal context the assistant should know about you -->
<!-- Example: "I work on a CLI tool written in Kotlin + Koog on JVM 21" -->
        """.trimIndent()
    }
}
