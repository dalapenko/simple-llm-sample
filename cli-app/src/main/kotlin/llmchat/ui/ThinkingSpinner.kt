package llmchat.ui

import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Animated braille spinner displayed while waiting for the LLM response.
 *
 * IMPORTANT: [stop] MUST be called before any Mordant or JLine output,
 * to clear the spinner line and avoid terminal corruption.
 */
class ThinkingSpinner(private val terminal: Terminal) {

    private val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var job: Job? = null
    private var startTime: Long = 0L

    /** Start the spinner animation. Runs on [Dispatchers.IO]. */
    fun start(scope: CoroutineScope, label: String = "Thinking...") {
        startTime = System.currentTimeMillis()
        job = scope.launch(Dispatchers.IO) {
            var frameIndex = 0
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val frame = frames[frameIndex % frames.size]
                val line = "  ${yellow(frame)} ${dim(label)} ${dim("${elapsed}s")}"
                // \r returns to start of line, \u001B[K clears to end of line
                print("\r\u001B[K$line")
                System.out.flush()
                frameIndex++
                delay(80)
            }
        }
    }

    /**
     * Stop the spinner and clear the line.
     * Call before any output or prompting for input.
     */
    fun stop() {
        job?.cancel()
        job = null
        print("\r\u001B[K")
        System.out.flush()
    }
}
