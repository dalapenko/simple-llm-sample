package llmchat.cli

/**
 * Represents commands that can be executed in the interactive CLI.
 */
sealed class Command {

    // ── Core commands ──────────────────────────────────────────────────────────

    data object Exit : Command()
    data object Help : Command()
    data object Clear : Command()
    data object History : Command()
    data object StrategyInfo : Command()

    data class Message(val content: String) : Command()
    data class Unknown(val input: String) : Command()

    // ── Branch commands (BranchingStrategy) ───────────────────────────────────

    /** List all branches. */
    data object BranchList : Command()

    /**
     * Create a new branch and switch to it.
     *
     * @property name             Name for the new branch.
     * @property fromCheckpoint   Optional checkpoint name to fork from.
     */
    data class BranchNew(val name: String, val fromCheckpoint: String? = null) : Command()

    /** Switch the active branch. */
    data class BranchSwitch(val name: String) : Command()

    // ── Checkpoint commands (BranchingStrategy) ────────────────────────────────

    /** List all saved checkpoints. */
    data object CheckpointList : Command()

    /** Save a named checkpoint at the current position. */
    data class CheckpointSave(val name: String) : Command()

    // ── Facts commands (StickyFactsStrategy) ──────────────────────────────────

    /** Display the current facts map. */
    data object FactsList : Command()

    /** Manually add or update a fact. */
    data class FactsSet(val key: String, val value: String) : Command()

    /** Remove a fact by key. */
    data class FactsDelete(val key: String) : Command()

    // ── Parsing ────────────────────────────────────────────────────────────────

    companion object {
        fun parse(input: String): Command {
            if (!input.startsWith("/")) return Message(input)

            val parts = input.split("\\s+".toRegex(), limit = 4)
            val cmd = parts[0]

            return when (cmd) {
                "/exit", "/quit" -> Exit
                "/help" -> Help
                "/clear" -> Clear
                "/history" -> History
                "/strategy" -> StrategyInfo

                // Branch commands
                "/branch" -> when (parts.getOrNull(1)) {
                    null, "list" -> BranchList
                    "new" -> {
                        val name = parts.getOrNull(2)
                            ?: return Unknown("/branch new requires a name")
                        val fromCheckpoint = if (parts.getOrNull(3) == "from") {
                            // /branch new <name> from <checkpoint>
                            // But we have limit=4, so part[3] would be "from <checkpoint>"
                            // Re-parse with higher limit to capture checkpoint name
                            val extended = input.split("\\s+".toRegex())
                            val fromIdx = extended.indexOf("from")
                            if (fromIdx >= 0 && fromIdx + 1 < extended.size) {
                                extended[fromIdx + 1]
                            } else null
                        } else if (parts.size > 3) {
                            // /branch new <name> <checkpoint>  (shorthand without "from")
                            parts[3]
                        } else null
                        BranchNew(name, fromCheckpoint)
                    }

                    "switch" -> {
                        val name = parts.getOrNull(2)
                            ?: return Unknown("/branch switch requires a branch name")
                        BranchSwitch(name)
                    }

                    else -> Unknown(input)
                }

                // Checkpoint commands
                "/checkpoint" -> when (parts.getOrNull(1)) {
                    null, "list" -> CheckpointList
                    "save" -> {
                        val name = parts.getOrNull(2)
                            ?: return Unknown("/checkpoint save requires a name")
                        CheckpointSave(name)
                    }

                    else -> Unknown(input)
                }

                // Facts commands
                "/facts" -> when (parts.getOrNull(1)) {
                    null, "list" -> FactsList
                    "set" -> {
                        val key = parts.getOrNull(2)
                            ?: return Unknown("/facts set requires KEY VALUE")
                        val value = parts.getOrNull(3)
                            ?: return Unknown("/facts set requires KEY VALUE")
                        FactsSet(key, value)
                    }

                    "delete" -> {
                        val key = parts.getOrNull(2)
                            ?: return Unknown("/facts delete requires a key")
                        FactsDelete(key)
                    }

                    else -> Unknown(input)
                }

                else -> Unknown(input)
            }
        }
    }
}
