package llmchat.ui

/**
 * Strips ANSI escape sequences from untrusted text (e.g., LLM responses)
 * before rendering. Prevents ANSI injection attacks where a crafted LLM
 * response could manipulate the terminal (cursor repositioning, clipboard
 * write via OSC-52, title hijack, etc.).
 */
object AnsiSanitizer {

    // Matches all 7-bit ANSI/VT100 escape sequences
    private val ANSI_ESCAPE_REGEX = Regex(
        """\x1B(?:\[[0-9;]*[A-Za-z]|\][^\x07\x1B]*[\x07\x1B\\]|[()][AB012]|[>=<NM78]|#[0-9])"""
    )

    /**
     * Remove all ANSI escape sequences from [text].
     * The returned string is safe to pass to Mordant's Markdown renderer.
     */
    fun strip(text: String): String = ANSI_ESCAPE_REGEX.replace(text, "")
}
