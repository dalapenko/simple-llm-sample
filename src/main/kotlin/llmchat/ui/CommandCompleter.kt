package llmchat.ui

import llmchat.cli.StrategyType
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * JLine3 [Completer] that provides Tab-completion for slash-commands.
 * Available completions depend on the active [strategyType].
 */
class CommandCompleter(private val strategyType: StrategyType) : Completer {

    override fun complete(
        reader: LineReader,
        line: ParsedLine,
        candidates: MutableList<Candidate>
    ) {
        val buffer = line.line()
        if (!buffer.startsWith("/")) return

        val parts = buffer.split("\\s+".toRegex())
        val cmd = parts[0]

        if (parts.size == 1) {
            val commands = mutableListOf(
                "/help", "/clear", "/history", "/strategy", "/exit", "/quit"
            )
            when (strategyType) {
                StrategyType.BRANCHING -> {
                    commands.add("/branch")
                    commands.add("/checkpoint")
                }

                StrategyType.STICKY_FACTS -> {
                    commands.add("/facts")
                }

                StrategyType.LAYERED -> {
                    commands.add("/memory")
                }

                else -> {}
            }
            commands
                .filter { it.startsWith(cmd) }
                .forEach { candidates.add(Candidate(it)) }
            return
        }

        if (parts.size == 2) {
            val sub = parts[1]
            val subCommands: List<String> = when (cmd) {
                "/branch" -> listOf("list", "new", "switch")
                "/checkpoint" -> listOf("list", "save")
                "/facts" -> listOf("list", "set", "delete")
                "/memory" -> listOf("add", "list", "delete", "clear")
                else -> emptyList()
            }
            subCommands
                .filter { it.startsWith(sub) }
                .forEach { candidates.add(Candidate(it)) }
        }
    }
}
