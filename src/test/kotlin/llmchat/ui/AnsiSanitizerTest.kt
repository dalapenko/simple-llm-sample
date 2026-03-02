package llmchat.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnsiSanitizerTest {

    @Test
    fun `plain text is returned unchanged`() {
        assertEquals("Hello, world!", AnsiSanitizer.strip("Hello, world!"))
    }

    @Test
    fun `empty string is returned unchanged`() {
        assertEquals("", AnsiSanitizer.strip(""))
    }

    @Test
    fun `SGR color reset sequence is stripped`() {
        val input = "\u001B[0mHello"
        assertEquals("Hello", AnsiSanitizer.strip(input))
    }

    @Test
    fun `bold and color SGR sequence is stripped`() {
        val input = "\u001B[1;31mError\u001B[0m"
        assertEquals("Error", AnsiSanitizer.strip(input))
    }

    @Test
    fun `cursor movement sequences are stripped`() {
        val input = "\u001B[2A\u001B[1B\u001B[3Ctext"
        assertEquals("text", AnsiSanitizer.strip(input))
    }

    @Test
    fun `OSC sequence (potential clipboard attack) is stripped`() {
        // OSC 52 clipboard write: ESC ] 52 ; c ; <base64> BEL
        val osc = "\u001B]52;c;dGVzdA==\u0007"
        val input = "before${osc}after"
        assertEquals("beforeafter", AnsiSanitizer.strip(input))
    }

    @Test
    fun `multiple ANSI sequences in one string are all stripped`() {
        val input = "\u001B[31mred\u001B[0m and \u001B[32mgreen\u001B[0m"
        assertEquals("red and green", AnsiSanitizer.strip(input))
    }

    @Test
    fun `result contains no ESC characters`() {
        val input = "\u001B[1mBold\u001B[0m \u001B[4mUnderline\u001B[0m"
        val result = AnsiSanitizer.strip(input)
        assertFalse(result.contains('\u001B'), "Result should have no ESC characters")
    }

    @Test
    fun `text with no ANSI sequences passes through intact`() {
        val input = "Line 1\nLine 2\nTabbed\t"
        assertEquals(input, AnsiSanitizer.strip(input))
    }

    @Test
    fun `screen erase sequence is stripped`() {
        val input = "\u001B[2Jclean"
        assertEquals("clean", AnsiSanitizer.strip(input))
    }
}
