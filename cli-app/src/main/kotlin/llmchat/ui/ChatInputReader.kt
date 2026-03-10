package llmchat.ui

import llmchat.cli.StrategyType
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.TerminalBuilder

/**
 * JLine3-based input reader replacing the raw [readlnOrNull] input loop.
 *
 * Features:
 * - Single Enter sends the message
 * - Backslash (\) at end of line enables multi-line continuation
 * - Arrow-key history navigation (in-memory only — no file for security)
 * - Tab-completion for slash-commands
 */
class ChatInputReader(strategyType: StrategyType) {

    private val jlineTerminal = TerminalBuilder.builder()
        .system(true)
        .build()

    val lineReader: LineReader = LineReaderBuilder.builder()
        .terminal(jlineTerminal)
        .completer(CommandCompleter(strategyType))
        .history(DefaultHistory())
        .variable(LineReader.HISTORY_FILE, null as Any?)
        .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
        .build()

    /**
     * Read one logical message from the user.
     *
     * @param prompt ANSI-styled prompt string rendered by JLine directly.
     * @return The user's input (trimmed), or null on Ctrl-D / Ctrl-C.
     */
    fun readInput(prompt: String): String? {
        return try {
            val firstLine = lineReader.readLine(prompt)

            if (!firstLine.endsWith("\\")) {
                return firstLine.trim()
            }

            // Multi-line continuation mode
            val lines = mutableListOf(firstLine.dropLast(1))
            while (true) {
                val continuation = lineReader.readLine("  ... ")
                if (!continuation.endsWith("\\")) {
                    lines.add(continuation)
                    break
                }
                lines.add(continuation.dropLast(1))
            }
            lines.joinToString("\n").trim()
        } catch (_: EndOfFileException) {
            null
        } catch (_: UserInterruptException) {
            null
        }
    }

    /** Close the JLine terminal. Call on application exit. */
    fun close() {
        runCatching { jlineTerminal.close() }
    }
}
